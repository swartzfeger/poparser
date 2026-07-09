package com.jay.parser.masterdata

import com.jay.parser.models.ItemCatalog
import com.jay.parser.models.MasterCustomer
import kotlinx.serialization.Serializable

@Serializable
data class MasterDataBundle(
    val itemCatalog: ItemCatalog,
    val customers: List<MasterCustomer>,
    val glAccounts: Map<String, String>,
    val qtyDiscountRules: List<MasterQtyDiscountRule>
)

@Serializable
data class MasterQtyDiscountRule(
    val customerId: String,
    val itemId: String,
    val qtyDiscountId: String,
    val priceLevel: String? = null,
    val breaks: List<MasterQtyDiscountBreak>
)

@Serializable
data class MasterQtyDiscountBreak(
    val minQty: Double,
    val discountPercent: Double
)

@Serializable
data class MasterDataMetadata(
    val sourceFilename: String,
    val importedAt: String,
    val customerCount: Int,
    val descriptionCount: Int,
    val pricedItemCount: Int,
    val glAccountCount: Int,
    val qtyDiscountRuleCount: Int
)

data class MasterDataImportResult(
    val metadata: MasterDataMetadata,
    val warnings: List<String>
)
