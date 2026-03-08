package com.jay.parser.models

import kotlinx.serialization.Serializable

@Serializable
data class MasterCustomer(
    val id: String,
    val name: String,
    val terms: String,
    val shipVia: String,
    val priceLevel: String
)