package com.jay.parser.parser

data class CandidateBlock(
    val lines: List<String>,
    val normalizedText: String,
    val isAddressLike: Boolean,
    val containsVendorFingerprint: Boolean,
    val containsCityStateZip: Boolean,
    val containsDockOrBuildingHint: Boolean
) {
    val rawText: String = lines.joinToString("\n")
}