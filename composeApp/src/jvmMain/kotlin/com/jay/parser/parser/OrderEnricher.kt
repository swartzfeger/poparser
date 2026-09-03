package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.GLAccountMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.mappers.QtyDiscountMapper
import com.jay.parser.models.ExportOrder
import com.jay.parser.models.ExportOrderLine
import com.jay.parser.models.ResolvedCustomer
import com.jay.parser.packaging.PackagingPlanner
import com.jay.parser.pdf.ParsedPdfFields

class OrderEnricher {

    private val packagingPlanner = PackagingPlanner()

    fun enrich(sourceFilename: String, parsed: ParsedPdfFields): ExportOrder {
        val resolvedCustomer = resolveCustomer(parsed)

        val lines = parsed.items
            .mapNotNull { item ->
                var sku = item.sku?.trim().orEmpty()
                if (sku.isBlank()) return@mapNotNull null

                val allKnownSkus = ItemMapper.getAllSkus()

                val isExactMatch = allKnownSkus.contains(sku)
                var description = if (isExactMatch) ItemMapper.getItemDescription(sku) else ""

                if (!isExactMatch && sku.equals("PLBL", ignoreCase = true)) {
                    description = "PAPER LABELS"
                } else if (!isExactMatch) {
                    val bestMatch = findUniqueClosestSku(sku, allKnownSkus, maxDistance = 2)

                    if (bestMatch != null) {
                        sku = bestMatch
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        description = ItemMapper.getItemDescription(sku).ifBlank { sku }
                    }
                }

                if (description.isBlank()) {
                    val bestMatch = findUniqueClosestSku(sku, allKnownSkus, maxDistance = 3)

                    if (bestMatch != null) {
                        sku = bestMatch
                        description = ItemMapper.getItemDescription(sku)
                    } else {
                        description = sku
                    }
                }

                val masterUnitPrice = ItemMapper.getItemPrice(
                    sku = sku,
                    priceLevel = resolvedCustomer?.priceLevel.orEmpty()
                )

                val mappedUnitPrice = if (
                    masterUnitPrice == 0.0 &&
                    sku.equals("PLBL", ignoreCase = true) &&
                    item.unitPrice != null
                ) {
                    item.unitPrice
                } else {
                    masterUnitPrice
                }

                val rawQty = item.quantity ?: 0.0
                val exportQty = getUomAdjustedQuantity(
                    sku = sku,
                    rawQuantity = rawQty,
                    sourceUom = item.uom,
                    sourceUnitPrice = item.unitPrice,
                    mappedUnitPrice = mappedUnitPrice,
                    resolvedCustomer = resolvedCustomer
                )

                val uomAdjustedUnitPrice = getUomAdjustedUnitPrice(
                    sku = sku,
                    mappedUnitPrice = mappedUnitPrice,
                    resolvedCustomer = resolvedCustomer
                )

                val qtyDiscountResult = QtyDiscountMapper.applyQtyDiscount(
                    customerId = resolvedCustomer?.id ?: resolvedCustomer?.name,
                    sku = sku,
                    quantity = exportQty,
                    unitPrice = uomAdjustedUnitPrice,
                    priceLevel = resolvedCustomer?.priceLevel
                )

                val resolvedUnitPrice = qtyDiscountResult.unitPrice

                val mappedGlAccount = GLAccountMapper.getGLAccount(sku)
                val glAccount = if (
                    mappedGlAccount.isBlank() &&
                    sku.equals("PLBL", ignoreCase = true)
                ) {
                    "4210"
                } else {
                    mappedGlAccount
                }

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

        val packagingResult = packagingPlanner.calculate(
            lines = lines,
            customerId = resolvedCustomer?.id.orEmpty()
        )

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
            lines = packagingResult.lines,
            packaging = packagingResult.summary
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
        sourceUom: String?,
        sourceUnitPrice: Double?,
        mappedUnitPrice: Double,
        resolvedCustomer: ResolvedCustomer?
    ): Double {
        val customerId = resolvedCustomer?.id?.uppercase().orEmpty()
        val normalizedSku = sku.uppercase().trim()

        if (customerId == "ECOLAB INC") {
            val normalizedUom = sourceUom?.uppercase()?.trim().orEmpty()
            if (normalizedUom in setOf("PCE", "PIECE")) {
                val divisor = getSkuUomDivisor(normalizedSku)
                return if (divisor != null && divisor > 0) rawQuantity / divisor else rawQuantity
            }

            if (normalizedUom.isNotBlank()) {
                return rawQuantity
            }

            // Preserve compatibility with older parsed records that did not retain UOM.
            if (normalizedSku == "CLK-50V-100" && rawQuantity >= 1000.0) {
                return rawQuantity / 50.0
            }
        }

        if (customerId == "DEVERE") {
            val vialCaseDivisor = normalizedSku
                .split("-")
                .firstNotNullOfOrNull { segment ->
                    Regex("""^(\d+)V$""").matchEntire(segment)?.groupValues?.get(1)?.toIntOrNull()
                }
            return if (vialCaseDivisor != null && vialCaseDivisor > 0) {
                rawQuantity / vialCaseDivisor
            } else {
                rawQuantity
            }
        }

        if (customerId == "ALPHA CHEMICAL SERVI") {
            val normalizedUom = sourceUom?.uppercase()?.trim().orEmpty()
            if (normalizedUom == "ITEM") {
                val divisor = getSkuUomDivisor(normalizedSku)
                return if (divisor != null && divisor > 0) rawQuantity / divisor else rawQuantity
            }

            return rawQuantity
        }

        /*
         * US Foods' Institutional Wholesale POs show individual-vial counts and
         * per-vial reference prices even though the UOM prints as CS. Sage orders
         * the sellable pack represented by the SKU's marked vial-count segment.
         */
        if (customerId == "US FOODS") {
            val divisor = getSkuUomDivisor(normalizedSku)
            return if (divisor != null && divisor > 0) rawQuantity / divisor else rawQuantity
        }

        /*
         * Explosia orders 220-200-2070 as 200 individual pieces, while Sage sells
         * the /200 bottle represented by the SKU's second numeric segment.
         */
        if (customerId == "EXPLOSIA AS") {
            val divisor = getSkuUomDivisor(normalizedSku)
            return if (divisor != null && divisor > 0) rawQuantity / divisor else rawQuantity
        }

        if (!isUomCustomer(customerId)) {
            return rawQuantity
        }

        /*
         * School Specialty mixes pricing conventions on the same PO layout.
         * Some rows price individual vials, so the printed quantity must be
         * divided by the SKU vial count. Other rows already use the sellable
         * pack price and quantity. Compare the PO price both ways with the
         * customer's master price and use the convention that fits best.
         */
        if (customerId == "SCHOOL SPECIALTY") {
            val divisor = getSkuUomDivisor(normalizedSku)
            if (divisor == null || divisor <= 1) return rawQuantity

            if (sourceUnitPrice != null && sourceUnitPrice > 0.0 && mappedUnitPrice > 0.0) {
                val directPriceDifference = kotlin.math.abs(sourceUnitPrice - mappedUnitPrice)
                val convertedPriceDifference = kotlin.math.abs(
                    sourceUnitPrice * divisor - mappedUnitPrice
                )

                return if (directPriceDifference <= convertedPriceDifference) {
                    rawQuantity
                } else {
                    rawQuantity / divisor
                }
            }

            // Preserve the previous behavior when either price is unavailable.
            return rawQuantity / divisor
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

        /*
         * Diversified exceptions:
         * - DFS-QAC-400B is handled at the Diversified layout level because their PO
         *   explicitly says PKG OF 12. Do not divide it back down here.
         * - 169-144V-100 is ordered by Diversified as the sellable PKG OF 144 quantity,
         *   not as individual vials. Keep the visible ordered quantity.
         */
        if (customerId == "DIVERSIFIED FOODSERV" && normalizedSku in setOf(
                "DFS-QAC-400B",
                "169-144V-100"
            )
        ) {
            return rawQuantity
        }

        /*
         * Eisco ERP-style POs can already send pack/case quantities for these
         * SKUs. Do not divide them by the quantity embedded in the SKU.
         *
         * 150-500V-100 comes in as UOM PK500 with quantity 1.
         * 185-12V-100 comes in as EA with quantity 10 and should remain 10.
        */
        if (customerId == "EISCO SCI" && normalizedSku in setOf(
                "150-500V-100",
                "185-12V-100"
            )
        ) {
            return rawQuantity
        }

        /*
         * AUTO-CHLOR quantities use the marked vial-count segment as the UOM
         * divisor. For example, 1V means divide by 1 and 144V means divide by
         * 144. The final numeric segment is strips per vial, not a quantity
         * conversion factor.
         */
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
            "ADVANCE PRODUCTS & S",
            "APOTHECARY PRODUCTS",
            "ATHEA LABORATORIES",
            "AUTO-CHLOR SYSTEM TN",
            "BAILEYS THERMOMETERS",
            "BLUE DRAGON DEFENSE",
            "BUNZL",
            "DIVERSIFIED FOODSERV",
            "CHARLOTTE PRODUCTS",
            "TCD PARTS",
            "DRAKE SPECIALITIES",
            "D.W. DAVIES",
            "EISCO SCI",
            "GASCO INDUSTRIAL",
            "HOME BREW OHIO",
            "SCHOOL SPECIALTY",
            "TAYLOR TECHNOLOGIES",
            "INTERCON CHEMICAL CO",
            "MIROIL USA, LLC",
            "NATIONAL CHEMICALS",
            "UNITED SCIENTIFIC",
            "DOVE MATERIAL",
            "BUTLER CHEMICAL PROD",
            "KROWNE METAL CORPORA",
            "JAYHAWK SALES TX",
            "JAYHAWK SALES WI",
            "WESTLAB"
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

private fun findUniqueClosestSku(
    sku: String,
    knownSkus: List<String>,
    maxDistance: Int
): String? {
    val candidates = knownSkus
        .map { candidate -> candidate to sku.levenshteinDistance(candidate) }
        .filter { (_, distance) -> distance <= maxDistance }

    val minimumDistance = candidates.minOfOrNull { (_, distance) -> distance } ?: return null
    val closest = candidates.filter { (_, distance) -> distance == minimumDistance }

    return closest.singleOrNull()?.first
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
