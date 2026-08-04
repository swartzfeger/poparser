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
    val glAccount: String,
    val itemDimensions: String? = null,
    val itemUnitVolumeCubicInches: Double? = null,
    val itemUnitWeightPounds: Double? = null
)

data class PackagingSummary(
    val orderPackedVolumeCubicInches: Double? = null,
    val orderWeightPounds: Double? = null,
    val totalBoxes: Int? = null,
    val boxPlan: String = "",
    val invoiceNote: String = "",
    val status: String = "Not calculated",
    val warnings: List<String> = emptyList()
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
    val lines: List<ExportOrderLine>,
    val packaging: PackagingSummary = PackagingSummary()
)
