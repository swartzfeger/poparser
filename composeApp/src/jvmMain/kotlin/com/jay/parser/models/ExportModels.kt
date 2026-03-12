package com.jay.parser.models

data class ResolvedCustomer(
    val id: String,
    val name: String,
    val terms: String,
    val shipVia: String,
    val priceLevel: String
)

data class ExportOrderLine(
    val sku: String,
    val description: String,
    val quantityRaw: Double,
    val quantityForExport: Double,
    val unitPriceReference: Double?,
    val unitPriceResolved: Double,
    val glAccount: String
)

data class ExportOrder(
    val sourceFilename: String,
    val customer: ResolvedCustomer?,
    val orderNumber: String,
    val customerNameRaw: String?,
    val shipToCustomer: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val city: String?,
    val state: String?,
    val zip: String?,
    val termsRaw: String?,
    val termsResolved: String?,
    val lines: List<ExportOrderLine>
)