package com.jay.parser.packaging

import kotlinx.serialization.Serializable

@Serializable
data class ProductPackaging(
    val lengthInches: Double? = null,
    val widthInches: Double? = null,
    val heightInches: Double? = null,
    val weightPounds: Double? = null,
    val inferredLength: Boolean = false
) {
    val hasDimensions: Boolean
        get() = listOf(lengthInches, widthInches, heightInches).all { it != null && it > 0.0 }

    val unitVolumeCubicInches: Double?
        get() = if (hasDimensions) {
            lengthInches!! * widthInches!! * heightInches!!
        } else {
            null
        }
}

@Serializable
data class PackagingDataMetadata(
    val sourceFilename: String,
    val importedAt: String,
    val productCount: Int,
    val dimensionedProductCount: Int,
    val weightedProductCount: Int,
    val completeProductCount: Int
)

data class PackagingDataImportResult(
    val metadata: PackagingDataMetadata,
    val warnings: List<String>
)
