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
                val sku = item.sku?.trim().orEmpty()
                if (sku.isBlank()) return@mapNotNull null

                val description = ItemMapper.getItemDescription(sku).ifBlank { sku }
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
        // these SKUs already arrive with the correct "Total Each" raw quantity
        // and must NOT be divided by the 4VB segment.
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