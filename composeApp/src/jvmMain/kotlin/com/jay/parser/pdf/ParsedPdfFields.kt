package com.jay.parser.pdf

data class ParsedPdfFields(
    val customerName: String? = null,
    val orderNumber: String? = null,
    val shipToCustomer: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val terms: String? = null,
    val items: List<ParsedPdfItem> = emptyList()
)

data class ParsedPdfItem(
    val sku: String? = null,
    val description: String? = null,
    val quantity: Double? = null,
    val unitPrice: Double? = null
)