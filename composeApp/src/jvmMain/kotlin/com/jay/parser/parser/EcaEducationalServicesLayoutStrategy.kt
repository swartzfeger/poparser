package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import com.jay.parser.pdf.PdfLine
import kotlin.math.abs

class EcaEducationalServicesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy, PositionedLayoutStrategy {

    override val name: String = "ECA EDUCATIONAL SERVICES"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("ECASCIENCEKITSERVICES") ||
                text.contains("ECAEDUCATIONALSERVICES") ||
                text.contains("STAKACS@ECAKITSERVICES.COM") ||
                text.contains("1981DALLAVODRIVE")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("ECASCIENCEKITSERVICES")) score += 120
        if (text.contains("ECAEDUCATIONALSERVICES")) score += 80
        if (text.contains("STAKACS@ECAKITSERVICES.COM")) score += 70
        if (text.contains("1981DALLAVODRIVE")) score += 70
        if (text.contains("COMMERCETOWNSHIP")) score += 60
        if (text.contains("PONUMBER:K")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "ECA EDUCATIONAL SERV",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    override fun parsePositioned(lines: List<PdfLine>): ParsedPdfFields {
        val parsed = parse(lines.map(PdfLine::text))
        val positionedItems = parsePositionedItems(lines)

        return if (positionedItems.isNotEmpty()) {
            parsed.copy(items = positionedItems)
        } else {
            parsed
        }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""PO\s+NUMBER:\s*K\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { "K$it" }
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("TERMS NET 30", ignoreCase = true) -> "Net 30"
            joined.contains("NET 30", ignoreCase = true) -> "Net 30"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { it.contains("BILL TO:", ignoreCase = true) && it.contains("SHIP TO:", ignoreCase = true) }
        if (idx >= 0) {
            val nameLine = lines.getOrNull(idx + 1).orEmpty()
            val addressLine = lines.getOrNull(idx + 2).orEmpty()
            val cityLine = lines.getOrNull(idx + 3).orEmpty()

            val shipToCustomer = splitBillToShipToLine(nameLine).second ?: "ECA SCIENCE KIT SERVICES"
            val address1 = splitBillToShipToLine(addressLine).second ?: "1981 DALLAVO DRIVE"
            val cityStateZip = splitBillToShipToLine(cityLine).second ?: "COMMERCE TOWNSHIP MI 48390"
            val parsed = parseCityStateZip(cityStateZip)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = address1,
                city = parsed?.city,
                state = parsed?.state,
                zip = parsed?.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = "ECA SCIENCE KIT SERVICES",
            addressLine1 = "1981 DALLAVO DRIVE",
            city = "COMMERCE TOWNSHIP",
            state = "MI",
            zip = "48390"
        )
    }

    private fun splitBillToShipToLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val billToPrefixes = listOf(
            "ECA Science Kit Services",
            "1981 Dallavo Drive",
            "Commerce Township, Michigan 48390"
        )

        for (prefix in billToPrefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val right = trimmed.removePrefix(prefix).trim()
                return prefix to right.ifBlank { null }
            }
        }

        val parts = Regex("""\s{2,}""").split(trimmed).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> null to trimmed
        }
    }

    private fun parseCityStateZip(text: String?): CityStateZip? {
        if (text.isNullOrBlank()) return null

        val cleaned = text
            .replace("COMMERCE TOWNSHIP MI", "COMMERCE TOWNSHIP MI ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = Regex("""^(.+?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim().uppercase(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val match = ITEM_ROW_PATTERN.find(line.trim()) ?: continue

            val vendorItem = match.groupValues[2].trim()
            val sku = normalizeEcaSku(match.groupValues[3])
            val descriptionFromPo = match.groupValues[4].trim()
            val quantity = match.groupValues[5].toDoubleOrNull() ?: continue
            val uom = match.groupValues[6].trim().uppercase()
            val unitPrice = match.groupValues[7].toDoubleOrNull() ?: continue

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                descriptionFromPo
            }

            val key = "$vendorItem|$sku|$quantity|$unitPrice"
            if (seen.add(key)) {
                items.add(
                    item(
                        sku = sku,
                        description = description,
                        quantity = quantity,
                        unitPrice = unitPrice,
                        uom = uom
                    )
                )
            }
        }

        return items
    }

    private fun parsePositionedItems(lines: List<PdfLine>): List<ParsedPdfItem> {
        return lines.mapNotNull { line ->
            val skuColumnTokens = line.tokens.filter { it.x >= SKU_COLUMN_START && it.x < SKU_COLUMN_END }
            if (skuColumnTokens.isEmpty()) return@mapNotNull null

            val rawSku = skuColumnTokens.sortedBy { it.x }.joinToString("") { it.text }.trim()
            val sku = normalizeEcaSku(rawSku)

            val baseY = skuColumnTokens
                .groupingBy { it.y }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: return@mapNotNull null

            val quantity = correctedOrBaseNumber(line, QUANTITY_COLUMN_START, QUANTITY_COLUMN_END, baseY)
                ?: return@mapNotNull null
            val unitPrice = correctedOrBaseNumber(line, UNIT_PRICE_COLUMN_START, UNIT_PRICE_COLUMN_END, baseY)
                ?: return@mapNotNull null
            val extendedPrice = correctedOrBaseNumber(line, EXTENDED_PRICE_COLUMN_START, EXTENDED_PRICE_COLUMN_END, baseY)
            val descriptionFromPo = textAtBaseY(line, DESCRIPTION_COLUMN_START, DESCRIPTION_COLUMN_END, baseY)
            val uom = textAtBaseY(line, UOM_COLUMN_START, UOM_COLUMN_END, baseY).uppercase()

            if (extendedPrice != null && abs(quantity * unitPrice - extendedPrice) > EXTENSION_TOLERANCE) {
                return@mapNotNull null
            }

            item(
                sku = sku,
                description = ItemMapper.getItemDescription(sku).ifBlank { descriptionFromPo },
                quantity = quantity,
                unitPrice = unitPrice,
                uom = uom.ifBlank { null }
            )
        }
    }

    private fun textAtBaseY(
        line: PdfLine,
        startX: Float,
        endX: Float,
        baseY: Float
    ): String {
        return line.tokens
            .filter {
                it.x >= startX &&
                    it.x < endX &&
                    abs(it.y - baseY) <= CORRECTION_Y_TOLERANCE
            }
            .sortedBy { it.x }
            .joinToString("") { it.text }
            .trim()
    }

    private fun correctedOrBaseNumber(
        line: PdfLine,
        startX: Float,
        endX: Float,
        baseY: Float
    ): Double? {
        val columnTokens = line.tokens.filter { it.x >= startX && it.x < endX }

        val corrected = columnTokens
            .filter { abs(it.y - baseY) > CORRECTION_Y_TOLERANCE }
            .sortedBy { it.x }
            .joinToString("") { it.text }
            .let(::parseMarkedNumber)

        if (corrected != null) return corrected

        return columnTokens
            .filter { abs(it.y - baseY) <= CORRECTION_Y_TOLERANCE }
            .sortedBy { it.x }
            .joinToString("") { it.text }
            .let(::parseMarkedNumber)
    }

    private fun parseMarkedNumber(value: String): Double? {
        val numericText = value.filter { it.isDigit() || it == '.' || it == ',' }
        return Regex("""\d[\d,]*(?:\.\d+)?""")
            .find(numericText)
            ?.value
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }

    private fun normalizeEcaSku(raw: String): String {
        val normalized = raw.trim().uppercase().replace(Regex("""[^A-Z0-9]"""), "")
        val knownSkus = ItemMapper.getAllSkus()

        knownSkus.singleOrNull { sku -> compactSku(sku) == normalized }?.let { return it }

        val zeroCorrected = normalized.replace('O', '0')
        return knownSkus.singleOrNull { sku -> compactSku(sku) == zeroCorrected }
            ?: raw.trim().uppercase()
    }

    private fun compactSku(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }

    private companion object {
        val ITEM_ROW_PATTERN = Regex(
            """^(\d+)\s+([A-Z0-9-]+)\s+([A-Z0-9]+)\s+(.+?)\s+([\d.]+)\s+([A-Z]+)\s+([\d.]+)\s+([\d.]+)$""",
            RegexOption.IGNORE_CASE
        )

        const val SKU_COLUMN_START = 105f
        const val SKU_COLUMN_END = 180f
        const val DESCRIPTION_COLUMN_START = 180f
        const val DESCRIPTION_COLUMN_END = 348f
        const val QUANTITY_COLUMN_START = 348f
        const val QUANTITY_COLUMN_END = 387f
        const val UOM_COLUMN_START = 387f
        const val UOM_COLUMN_END = 438f
        const val UNIT_PRICE_COLUMN_START = 438f
        const val UNIT_PRICE_COLUMN_END = 503f
        const val EXTENDED_PRICE_COLUMN_START = 503f
        const val EXTENDED_PRICE_COLUMN_END = 575f
        const val CORRECTION_Y_TOLERANCE = 0.25f
        const val EXTENSION_TOLERANCE = 0.02
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )
}
