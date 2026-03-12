package com.jay.parser.parser

data class InterpretedShipTo(
    val shipToCustomer: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null
)

class ShipToInterpreter {

    fun interpret(block: CandidateBlock): InterpretedShipTo {
        val cleanedLines = block.lines
            .map { cleanLine(it) }
            .map { trimAfterZip(it) }
            .filter { it.isNotBlank() }
            .filterNot { isNoiseLine(it) }

        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val addressCandidates = cleanedLines.toMutableList()

        for (line in cleanedLines) {
            val cityStateZip = extractCityStateZip(line)
            if (cityStateZip != null) {
                city = cityStateZip.city
                state = cityStateZip.state
                zip = cityStateZip.zip
                addressCandidates.remove(line)
                break
            }
        }

        val remaining = addressCandidates
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (remaining.isNotEmpty()) {
            shipToCustomer = remaining.lastOrNull { looksLikeCompanyOrLocationName(it) } ?: remaining.lastOrNull()
        }

        val addressish = remaining.filterNot { it == shipToCustomer }

        if (addressish.isNotEmpty()) {
            addressLine1 = addressish.getOrNull(0)
            addressLine2 = addressish.getOrNull(1)
        }

        return InterpretedShipTo(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )

    private fun extractCityStateZip(line: String): CityStateZip? {
        val regex1 = Regex("""([A-Za-z .'-]+),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")
        val regex2 = Regex("""([A-Za-z .'-]+)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")

        val match1 = regex1.find(line)
        if (match1 != null) {
            return CityStateZip(
                city = match1.groupValues[1].trim(),
                state = match1.groupValues[2].trim(),
                zip = match1.groupValues[3].trim()
            )
        }

        val match2 = regex2.find(line)
        if (match2 != null) {
            return CityStateZip(
                city = match2.groupValues[1].trim(),
                state = match2.groupValues[2].trim(),
                zip = match2.groupValues[3].trim()
            )
        }

        return null
    }

    private fun trimAfterZip(line: String): String {
        val zipMatch = Regex("""\b\d{5}(?:-\d{4})?\b""").find(line) ?: return line
        return line.substring(0, zipMatch.range.last + 1).trim()
    }

    private fun cleanLine(line: String): String {
        return line
            .replace(Regex("""\bDate\s*:?\s*.+$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bMEDLINE PO NUMBER\s*:?\s*.+$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bPO NUMBER\s*:?\s*.+$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\*+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isNoiseLine(line: String): Boolean {
        val upper = line.uppercase()

        if (upper.isBlank()) return true
        if (upper.startsWith("AS SHIPPER#")) return true
        if (upper.contains("BILL OF LADING")) return true
        if (upper.contains("IMPORTANT")) return true
        if (upper.startsWith("PAGE ")) return true
        if (upper == "US" || upper == "USA") return true

        return false
    }

    private fun looksLikeCompanyOrLocationName(line: String): Boolean {
        val upper = line.uppercase()

        return upper.contains("MEDLINE") ||
                upper.contains("INDUSTRIES") ||
                upper.contains("LLC") ||
                upper.contains("INC") ||
                upper.contains("LP") ||
                upper.contains("CORP") ||
                upper.contains("DOCK") ||
                upper.contains("BUILDING") ||
                upper.contains("TRANSSHIP") ||
                upper.contains("CROSS DOCK")
    }
}