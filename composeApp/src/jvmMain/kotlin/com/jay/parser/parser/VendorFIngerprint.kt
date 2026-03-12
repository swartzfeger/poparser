package com.jay.parser.parser

object VendorFingerprint {

    private val requiredSignals = setOf(
        "PRECISION",
        "LABORATORIES"
    )

    private val strongLocationSignals = setOf(
        "AIRPARK",
        "COTTONWOOD",
        "86326"
    )

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""

        return text
            .uppercase()
            .replace("S.", "S")
            .replace("RD.", "RD")
            .replace("ROAD", "RD")
            .replace("SOUTH", "S")
            .replace(Regex("""[^A-Z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun containsVendorFingerprint(text: String?): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false

        val tokens = normalized.split(" ").filter { it.isNotBlank() }.toSet()

        val hasRequired = requiredSignals.all { it in tokens }
        val locationHits = strongLocationSignals.count { it in tokens }

        if (hasRequired) return true
        if (locationHits >= 2) return true

        return false
    }
}