package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.GLAccountMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.models.ExportOrder
import com.jay.parser.models.ExportOrderLine
import com.jay.parser.models.ResolvedCustomer
import com.jay.parser.pdf.ParsedPdfFields

class OrderEnricher {

    fun enrich(sourceFilename: String, parsed: ParsedPdfFields): ExportOrder {
        val resolvedCustomer = resolveCustomer(parsed)

        val lines = parsed.items
            .mapNotNull { item ->
                var sku = item.sku?.trim().orEmpty()
                if (sku.isBlank()) return@mapNotNull null

                val allKnownSkus = ItemMapper.getAllSkus()

                val isExactMatch = allKnownSkus.contains(sku)
                var description = if (isExactMatch) ItemMapper.getItemDescription(sku) else ""

                if (!isExactMatch) {
                    val bestMatch = allKnownSkus
                        .associateWith { sku.levenshteinDistance(it) }
                        .filterValues { distance -> distance <= 2 }
                        .minByOrNull { it.value }
                        ?.key

                    if (bestMatch != null) {
                        sku = bestMatch
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        description = ItemMapper.getItemDescription(sku).ifBlank { sku }
                    }
                }

                if (description.isBlank()) {
                    val bestMatch = allKnownSkus
                        .associateWith { sku.levenshteinDistance(it) }
                        .filterValues { distance -> distance <= 3 }
                        .minByOrNull { it.value }
                        ?.key

                    if (bestMatch != null) {
                        sku = bestMatch
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        description = sku
                    }
                }

                val rawQty = item.quantity ?: 0.0
                val exportQty = getUomAdjustedQuantity(
                    sku = sku,
                    rawQuantity = rawQty,
                    resolvedCustomer = resolvedCustomer
                )

                val mappedUnitPrice = ItemMapper.getItemPrice(
                    sku = sku,
                    priceLevel = resolvedCustomer?.priceLevel.orEmpty()
                )

                val resolvedUnitPrice = getUomAdjustedUnitPrice(
                    sku = sku,
                    mappedUnitPrice = mappedUnitPrice,
                    resolvedCustomer = resolvedCustomer
                )

                val glAccount = GLAccountMapper.getGLAccount(sku)

                ExportOrderLine(
                    sku = sku,
                    description = description,
                    quantityRaw = rawQty,
                    quantityForExport = exportQty,
                    unitPriceReference = item.unitPrice,
                    unitPriceResolved = resolvedUnitPrice,
                    glAccount = glAccount
                )
            }

        return ExportOrder(
            sourceFilename = sourceFilename,
            customer = resolvedCustomer,
            orderNumber = parsed.orderNumber.orEmpty(),
            customerNameRaw = parsed.customerName,
            shipToCustomer = parsed.shipToCustomer,
            addressLine1 = parsed.addressLine1,
            addressLine2 = parsed.addressLine2,
            city = parsed.city,
            state = parsed.state,
            zip = parsed.zip,
            termsRaw = parsed.terms,
            termsResolved = resolvedCustomer?.terms ?: parsed.terms,
            lines = lines
        )
    }

    private fun resolveCustomer(parsed: ParsedPdfFields): ResolvedCustomer? {
        val lookupSource = parsed.customerName
            ?: parsed.shipToCustomer
            ?: return null

        val match = CustomerMapper.lookupCustomer(lookupSource) ?: return null

        return ResolvedCustomer(
            id = match.id,
            name = match.name,
            terms = match.terms,
            shipVia = match.shipVia,
            priceLevel = match.priceLevel
        )
    }

    private fun getUomAdjustedQuantity(
        sku: String,
        rawQuantity: Double,
        resolvedCustomer: ResolvedCustomer?
    ): Double {
        val customerId = resolvedCustomer?.id?.uppercase().orEmpty()
        val normalizedSku = sku.uppercase().trim()

        if (!isUomCustomer(customerId)) {
            return rawQuantity
        }

        // Jayhawk WI is the exception: keep ordered quantity, divide mapped price instead.
        if (customerId == "JAYHAWK SALES WI") {
            return rawQuantity
        }

        if (customerId == "DOVE MATERIAL" && normalizedSku in setOf(
                "145-4VB-100",
                "106-QR5-4VB-100",
                "145-QR5-2VB-100"
            )
        ) {
            return rawQuantity
        }

        val divisor = getSkuUomDivisor(normalizedSku)
        return if (divisor != null && divisor > 0) rawQuantity / divisor else rawQuantity
    }

    private fun getUomAdjustedUnitPrice(
        sku: String,
        mappedUnitPrice: Double,
        resolvedCustomer: ResolvedCustomer?
    ): Double {
        val customerId = resolvedCustomer?.id?.uppercase().orEmpty()
        val normalizedSku = sku.uppercase().trim()

        if (!isUomCustomer(customerId)) {
            return mappedUnitPrice
        }

        // Jayhawk WI is the exception: divide mapped price by divisor.
        if (customerId == "JAYHAWK SALES WI") {
            val divisor = getSkuUomDivisor(normalizedSku)
            return if (divisor != null && divisor > 0) mappedUnitPrice / divisor else mappedUnitPrice
        }

        if (customerId == "DOVE MATERIAL" && normalizedSku in setOf(
                "145-4VB-100",
                "106-QR5-4VB-100"
            )
        ) {
            return mappedUnitPrice
        }

        return mappedUnitPrice
    }

    private fun isUomCustomer(customerId: String): Boolean {
        val uomCustomerIds = setOf(
            "DIVERSIFIED FOODSERV",
            "TCD PARTS",
            "DRAKE SPECIALITIES",
            "EISCO SCI",
            "NATIONAL CHEMICALS",
            "DOVE MATERIAL",
            "KROWNE METAL CORPORA",
            "JAYHAWK SALES TX",
            "JAYHAWK SALES WI"
        )
        return uomCustomerIds.contains(customerId)
    }

    private fun getSkuUomDivisor(normalizedSku: String): Int? {
        val allowedDivisors = setOf(
            1, 2, 4, 5, 6, 8, 10, 12, 20, 24, 25, 40, 50,
            100, 144, 200, 250, 500, 1000, 10000
        )

        val segments = normalizedSku.split("-")

        val markedFactor = segments
            .asSequence()
            .mapNotNull { segment ->
                val match = Regex("""^(\d+)(?:V|B|VB)$""").find(segment) ?: return@mapNotNull null
                match.groupValues[1].toIntOrNull()
            }
            .firstOrNull { it in allowedDivisors }

        if (markedFactor != null && markedFactor > 0) {
            return markedFactor
        }

        val secondSegmentFactor = segments
            .getOrNull(1)
            ?.let { Regex("""^(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.takeIf { it in allowedDivisors }

        if (secondSegmentFactor != null && secondSegmentFactor > 0) {
            return secondSegmentFactor
        }

        return null
    }
}

/**
 * Calculates the Levenshtein distance between two strings.
 * A lower number means the strings are more similar. (0 = exact match)
 */
private fun String.levenshteinDistance(other: String): Int {
    val lhsLength = this.length
    val rhsLength = other.length

    var cost = IntArray(lhsLength + 1) { it }
    var newCost = IntArray(lhsLength + 1) { 0 }

    for (i in 1..rhsLength) {
        newCost[0] = i
        for (j in 1..lhsLength) {
            val match = if (this[j - 1] == other[i - 1]) 0 else 1
            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1
            newCost[j] = minOf(costInsert, costDelete, costReplace)
        }
        val swap = cost
        cost = newCost
        newCost = swap
    }
    return cost[lhsLength]
}