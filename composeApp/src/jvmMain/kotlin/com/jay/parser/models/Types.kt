package com.jay.parser.models

data class POItem(
    val sku: String = "",
    val description: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0
)

data class POData(
    val filename: String = "",
    val customerName: String = "",
    val orderNumber: String = "",
    val shipToCustomer: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val terms: String = "",
    val items: List<POItem> = emptyList()
)

enum class ProcessingStatus {
    IDLE,
    PROCESSING,
    COMPLETED,
    ERROR
}

data class ProcessingState(
    val status: ProcessingStatus = ProcessingStatus.IDLE,
    val progress: Int = 0,
    val message: String = ""
)