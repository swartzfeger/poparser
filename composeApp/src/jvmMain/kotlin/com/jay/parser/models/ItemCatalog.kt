package com.jay.parser.models

import kotlinx.serialization.Serializable

@Serializable
data class ItemCatalog(
    val prices: Map<String, Map<String, Double>>,
    val descriptions: Map<String, String>
)