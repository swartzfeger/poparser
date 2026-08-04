package com.jay.parser.packaging

import com.jay.parser.models.ExportOrderLine
import com.jay.parser.models.PackagingSummary
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor

class PackagingPlanner(
    private val productsProvider: () -> Map<String, ProductPackaging> = { PackagingDataStore.current() },
    private val boxes: List<ShippingBox> = ShippingBoxes.all
) {

    data class Result(
        val lines: List<ExportOrderLine>,
        val summary: PackagingSummary
    )

    fun calculate(lines: List<ExportOrderLine>): Result {
        val products = productsProvider()
        val resolvedProducts = lines.associate { line -> line.sku to findProduct(products, line.sku) }
        val measuredLines = lines.map { line ->
            val product = resolvedProducts[line.sku]
            line.copy(
                itemDimensions = product?.dimensionsLabel(),
                itemUnitVolumeCubicInches = product?.unitVolumeCubicInches?.roundMeasurement(),
                itemUnitWeightPounds = product?.weightPounds?.roundMeasurement()
            )
        }

        val shippableLines = measuredLines.mapIndexedNotNull { index, line ->
            if (line.quantityForExport > 0.0) IndexedLine(index + 1, line) else null
        }
        if (shippableLines.isEmpty()) {
            return Result(
                measuredLines,
                PackagingSummary(status = "Review: no shippable item quantities")
            )
        }

        val missingDimensions = shippableLines
            .filter { resolvedProducts[it.line.sku]?.hasDimensions != true }
            .map { it.line.sku }
            .distinct()
        val missingWeights = shippableLines
            .filter { resolvedProducts[it.line.sku]?.weightPounds == null }
            .map { it.line.sku }
            .distinct()

        val warnings = mutableListOf<String>()
        if (missingDimensions.isNotEmpty()) {
            warnings += "Missing dimensions: ${missingDimensions.joinToString(", ")}"
        }
        if (missingWeights.isNotEmpty()) {
            warnings += "Missing weight: ${missingWeights.joinToString(", ")}"
        }

        val hasAllDimensions = missingDimensions.isEmpty()
        val hasAllWeights = missingWeights.isEmpty()
        val totalItemVolume = if (hasAllDimensions) {
            shippableLines.sumOf { line ->
                resolvedProducts.getValue(line.line.sku)!!.unitVolumeCubicInches!! * packagingQuantity(line.line)
            }
        } else {
            null
        }
        val totalWeight = if (hasAllWeights) {
            shippableLines.sumOf { line ->
                resolvedProducts.getValue(line.line.sku)!!.weightPounds!! * packagingQuantity(line.line)
            }
        } else {
            null
        }

        if (!hasAllDimensions) {
            return Result(
                lines = measuredLines,
                summary = PackagingSummary(
                    orderWeightPounds = totalWeight?.roundMeasurement(),
                    status = reviewStatus(warnings),
                    warnings = warnings
                )
            )
        }

        val units = buildUnits(shippableLines, resolvedProducts)
        val packing = pack(units)
        if (packing.failure != null) warnings += packing.failure

        val boxPlan = if (packing.failure == null) {
            packing.loads
                .groupingBy { it.box }
                .eachCount()
                .entries
                .sortedBy { it.key.id }
                .joinToString("; ") { (box, count) ->
                    "${count}x Box ${box.id} (${box.dimensionsLabel})"
                }
        } else {
            ""
        }
        val invoiceNote = if (packing.failure == null) buildInvoiceNote(packing.loads) else ""

        return Result(
            lines = measuredLines,
            summary = PackagingSummary(
                orderPackedVolumeCubicInches = totalItemVolume
                    ?.div(MAX_FILL_RATIO)
                    ?.roundMeasurement(),
                orderWeightPounds = totalWeight?.roundMeasurement(),
                totalBoxes = packing.loads.size.takeIf { packing.failure == null },
                boxPlan = boxPlan,
                invoiceNote = invoiceNote,
                status = if (warnings.isEmpty()) "Complete" else reviewStatus(warnings),
                warnings = warnings
            )
        )
    }

    private fun buildUnits(
        lines: List<IndexedLine>,
        products: Map<String, ProductPackaging?>
    ): List<PackableUnit> = buildList {
        lines.forEach { indexedLine ->
            val line = indexedLine.line
            val product = products.getValue(line.sku)!!
            val quantity = packagingQuantity(line)
            val wholeUnits = floor(quantity).toInt()
            repeat(wholeUnits) { add(product.toUnit(line.sku, indexedLine.number, 1.0)) }

            val fraction = quantity - wholeUnits
            if (fraction > QUANTITY_EPSILON) {
                add(product.toUnit(line.sku, indexedLine.number, fraction))
            }
        }
    }

    private fun packagingQuantity(line: ExportOrderLine): Double =
        if (NO_PARTIALS_UOM_PATTERN.containsMatchIn(line.sku)) {
            ceil(line.quantityForExport - QUANTITY_EPSILON)
        } else {
            line.quantityForExport
        }

    private fun pack(units: List<PackableUnit>): PackingResult {
        val sortedUnits = units.sortedWith(
            compareByDescending<PackableUnit> { it.volume }
                .thenByDescending { maxOf(it.length, it.width, it.height) }
        )
        val totalVolume = sortedUnits.sumOf { it.volume }
        val totalWeight = sortedUnits.sumOf { it.weight }

        val singleBox = boxes
            .asSequence()
            .filter { totalVolume <= it.usableVolumeCubicInches + MEASUREMENT_EPSILON }
            .filter { totalWeight <= MAX_BOX_WEIGHT_POUNDS + MEASUREMENT_EPSILON }
            .filter { box -> sortedUnits.all { it.fits(box) } }
            .minByOrNull { it.volumeCubicInches }
        if (singleBox != null) {
            val load = BoxLoad(singleBox)
            sortedUnits.forEach(load::add)
            return PackingResult(listOf(load), null)
        }

        val loads = mutableListOf<BoxLoad>()
        sortedUnits.forEachIndexed { index, unit ->
            if (unit.weight > MAX_BOX_WEIGHT_POUNDS + MEASUREMENT_EPSILON) {
                return PackingResult(loads, "${unit.sku} exceeds the 50 lb box limit")
            }

            val existing = loads
                .filter { it.canFit(unit) }
                .minByOrNull { it.remainingVolumeAfter(unit) }

            if (existing != null) {
                existing.add(unit)
                return@forEachIndexed
            }

            val candidates = boxes.filter { unit.fits(it) && unit.volume <= it.usableVolumeCubicInches }
            if (candidates.isEmpty()) {
                return PackingResult(loads, "${unit.sku} does not fit any configured box")
            }

            val remainingVolume = sortedUnits.drop(index).sumOf { it.volume }
            val largestCapacity = candidates.maxOf { it.usableVolumeCubicInches }
            val targetCapacity = minOf(remainingVolume, largestCapacity)
            val selectedBox = candidates
                .filter { it.usableVolumeCubicInches + MEASUREMENT_EPSILON >= targetCapacity }
                .minByOrNull { it.volumeCubicInches }
                ?: candidates.maxBy { it.usableVolumeCubicInches }

            loads += BoxLoad(selectedBox).also { it.add(unit) }
        }

        return PackingResult(loads, null)
    }

    private fun findProduct(
        products: Map<String, ProductPackaging>,
        rawSku: String
    ): ProductPackaging? {
        val sku = rawSku.trim().uppercase()
        return products[sku]
            ?: products[sku.removePrefix("SPC-")]
            ?: products[sku.removePrefix("DFS-")]
    }

    private fun ProductPackaging.toUnit(
        sku: String,
        lineNumber: Int,
        quantityFactor: Double
    ): PackableUnit = PackableUnit(
        sku = sku,
        lineNumber = lineNumber,
        length = lengthInches!!,
        width = widthInches!!,
        height = heightInches!!,
        volume = unitVolumeCubicInches!! * quantityFactor,
        weight = weightPounds?.times(quantityFactor) ?: 0.0,
        weightKnown = weightPounds != null
    )

    private fun buildInvoiceNote(loads: List<BoxLoad>): String {
        return loads
            .groupingBy { load ->
                InvoiceNoteGroup(
                    box = load.box,
                    weight = load.totalWeight.roundMeasurement(),
                    weightKnown = load.weightKnown,
                    lineNumbers = load.lineNumbers
                )
            }
            .eachCount()
            .entries
            .joinToString(" ") { (group, count) ->
                val weight = if (group.weightKnown) group.weight.formatMeasurement() else "?"
                val containedLines = formatContainedLines(group.lineNumbers)
                "($count @ $weight lbs, Box #${group.box.id} (${group.box.dimensionsLabel}) $containedLines)"
            }
    }

    private fun formatContainedLines(lineNumbers: List<Int>): String {
        if (lineNumbers.size == 1) return "Contains Line ${lineNumbers.single()}"

        val prefix = lineNumbers.dropLast(1).joinToString(", ")
        return "Contains Lines $prefix and ${lineNumbers.last()}"
    }

    private fun Double.formatMeasurement(): String = BigDecimal.valueOf(this)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    private fun ProductPackaging.dimensionsLabel(): String = listOf(
        lengthInches?.clean() ?: "?",
        widthInches?.clean() ?: "?",
        heightInches?.clean() ?: "?"
    ).joinToString(" x ")

    private fun reviewStatus(warnings: List<String>): String =
        "Review: ${warnings.joinToString("; ")}"

    private fun Double.roundMeasurement(): Double = BigDecimal.valueOf(this)
        .setScale(3, RoundingMode.HALF_UP)
        .toDouble()

    private data class PackableUnit(
        val sku: String,
        val lineNumber: Int,
        val length: Double,
        val width: Double,
        val height: Double,
        val volume: Double,
        val weight: Double,
        val weightKnown: Boolean
    ) {
        fun fits(box: ShippingBox): Boolean = box.fits(length, width, height)
    }

    private data class IndexedLine(
        val number: Int,
        val line: ExportOrderLine
    )

    private data class InvoiceNoteGroup(
        val box: ShippingBox,
        val weight: Double,
        val weightKnown: Boolean,
        val lineNumbers: List<Int>
    )

    private data class PackingResult(
        val loads: List<BoxLoad>,
        val failure: String?
    )

    private class BoxLoad(
        val box: ShippingBox
    ) {
        private var usedVolume = 0.0
        private var usedWeight = 0.0
        private val units = mutableListOf<PackableUnit>()

        val totalWeight: Double
            get() = usedWeight

        val weightKnown: Boolean
            get() = units.all { it.weightKnown }

        val lineNumbers: List<Int>
            get() = units.map { it.lineNumber }.distinct().sorted()

        fun canFit(unit: PackableUnit): Boolean =
            unit.fits(box) &&
                    usedVolume + unit.volume <= box.usableVolumeCubicInches + MEASUREMENT_EPSILON &&
                    usedWeight + unit.weight <= MAX_BOX_WEIGHT_POUNDS + MEASUREMENT_EPSILON

        fun remainingVolumeAfter(unit: PackableUnit): Double =
            box.usableVolumeCubicInches - usedVolume - unit.volume

        fun add(unit: PackableUnit) {
            usedVolume += unit.volume
            usedWeight += unit.weight
            units += unit
        }
    }

    private companion object {
        const val MAX_FILL_RATIO = 0.90
        const val MAX_BOX_WEIGHT_POUNDS = 50.0
        const val QUANTITY_EPSILON = 0.000001
        const val MEASUREMENT_EPSILON = 0.000001
        val NO_PARTIALS_UOM_PATTERN = Regex("(?:^|-)(?:12|24|400|500)V(?:-|$)", RegexOption.IGNORE_CASE)
    }
}
