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

        // Inside OrderEnricher.kt -> enrich()
        val lines = parsed.items
            .mapNotNull { item ->
                var sku = item.sku?.trim().orEmpty()
                if (sku.isBlank()) return@mapNotNull null

                val allKnownSkus = ItemMapper.getAllSkus()

                // 1. STRICT EXACT MATCH FIRST
                val isExactMatch = allKnownSkus.contains(sku)
                var description = if (isExactMatch) ItemMapper.getItemDescription(sku) else ""

                // 2. FUZZY MATCH FALLBACK
                if (!isExactMatch) {
                    val bestMatch = allKnownSkus
                        .associateWith { sku.levenshteinDistance(it) }
                        .filterValues { distance -> distance <= 2 } // Tightened to 2 to prevent wild guesses
                        .minByOrNull { it.value }?.key

                    if (bestMatch != null) {
                        sku = bestMatch
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        // If fuzzy fails, fall back to the startsWith logic so we at least get a description
                        description = ItemMapper.getItemDescription(sku).ifBlank { sku }
                    }
                }

                // ... (rest of the quantity and pricing logic remains the same)

                // 2. FUZZY MATCH FALLBACK (Catches OCR errors)
                // If description is blank, the exact match failed.
                if (description.isBlank()) {
                    // Fetch your full list of valid database SKUs
                    val allKnownSkus = ItemMapper.getAllSkus()

                    val bestMatch = allKnownSkus
                        .associateWith { sku.levenshteinDistance(it) }
                        .filterValues { distance -> distance <= 3 } // Strict threshold
                        .minByOrNull { it.value }?.key

                    if (bestMatch != null) {
                        sku = bestMatch // Overwrite the bad OCR SKU with the valid DB SKU
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        description = sku // Fallback to raw parsed text if no fuzzy match is close enough
                    }
                }

                val rawQty = item.quantity ?: 0.0
                val exportQty = getUomAdjustedQuantity(
                    sku = sku,
                    rawQuantity = rawQty,
                    resolvedCustomer = resolvedCustomer
                )
                val resolvedUnitPrice = ItemMapper.getItemPrice(
                    sku = sku,
                    priceLevel = resolvedCustomer?.priceLevel.orEmpty()
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

        val uomCustomerIds = setOf(
            "DIVERSIFIED FOODSERV",
            "TCD PARTS",
            "DRAKE SPECIALITIES",
            "EISCO SCI",
            "NATIONAL CHEMICALS",
            "DOVE MATERIAL",
            "KROWNE METAL CORPORA"
        )

        if (!uomCustomerIds.contains(customerId)) {
            return rawQuantity
        }

        // Dove-specific exceptions:
        if (customerId == "DOVE MATERIAL" && normalizedSku in setOf(
                "145-4VB-100",
                "106-QR5-4VB-100"
            )
        ) {
            return rawQuantity
        }

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
            return rawQuantity / markedFactor
        }

        val secondSegmentFactor = segments
            .getOrNull(1)
            ?.let { Regex("""^(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.takeIf { it in allowedDivisors }

        if (secondSegmentFactor != null && secondSegmentFactor > 0) {
            return rawQuantity / secondSegmentFactor
        }

        return rawQuantity
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