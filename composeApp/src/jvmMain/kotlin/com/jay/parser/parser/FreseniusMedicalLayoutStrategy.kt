package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class FreseniusMedicalLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "FRESENIUS MEDICAL"

    override fun matches(lines: List<String>): Boolean {
        val variants = lines.flatMap { line ->
            listOf(
                normalizeForMatch(line),
                normalizeForMatch(collapseDoubledChars(line))
            )
        }

        val hasFresenius = variants.any { it.contains("FRESENIUS MEDICAL CARE NORTH AMERICA") }
        val hasPurchaseOrder = variants.any { it.contains("PURCHASE ORDER") }
        val hasTenDigitPo = variants.any { Regex("""\b\d{10}\b""").containsMatchIn(it) }

        return hasFresenius && (hasPurchaseOrder || hasTenDigitPo)
    }

    override fun score(lines: List<String>): Int {
        val variants = lines.flatMap { line ->
            listOf(
                normalizeForMatch(line),
                normalizeForMatch(collapseDoubledChars(line))
            )
        }

        var score = 0
        if (variants.any { it.contains("FRESENIUS MEDICAL CARE NORTH AMERICA") }) score += 60
        if (variants.any { it.contains("PURCHASE ORDER") }) score += 25
        if (variants.any { Regex("""\b\d{10}\b""").containsMatchIn(it) }) score += 20
        if (variants.any { it.contains("BILL TO") }) score += 10
        if (variants.any { it.contains("FREIGHT TERMS") }) score += 10
        if (variants.any { it.contains("PURCHASING: FOB DESTINATION") }) score += 10
        if (variants.any { it.contains("YOUR MATERIAL NUMBER") }) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        val orderNumber = parseOrderNumber(textLines)
        val shipTo = parseShipTo(textLines)
        val items = parseItems(textLines)

        return ParsedPdfFields(
            customerName = "FRESENIUS MEDICAL CARE NORTH AMERICA",
            orderNumber = orderNumber,
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = items
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val variants = listOf(
                normalizeForMatch(line),
                normalizeForMatch(collapseDoubledChars(line))
            )

            for (value in variants) {
                Regex("""PURCHASE ORDER\s+([0-9-]+)""", RegexOption.IGNORE_CASE)
                    .find(value)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.let { return it }

                Regex("""\b(\d{10})\b""")
                    .find(value)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.let { return it }
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): InterpretedShipTo {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val variants = lines.map { normalizeForMatch(collapseDoubledChars(it)) }

        for (value in variants) {
            if (shipToCustomer == null && value.contains("FRESENIUS USA MANUFACTURING")) {
                shipToCustomer = "FRESENIUS USA MANUFACTURING, INC. D/B/A FRESENIUS MEDICAL CARE NORTH AMERICA"
            }

            if (addressLine1 == null && value.contains("371 S. ROYAL LANE")) {
                addressLine1 = "371 S. Royal Lane"
            }

            if (addressLine2 == null && value.contains("FRESENIUS MEDICAL CARE NORTH AMERICA") && !value.contains("FREIGHT TERMS")) {
                addressLine2 = "FRESENIUS MEDICAL CARE NORTH AMERICA"
            }

            if (city == null) {
                val csz = Regex("""(DFW AIRPORT)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")
                    .find(value)

                if (csz != null) {
                    city = csz.groupValues[1].trim()
                    state = csz.groupValues[2].trim()
                    zip = csz.groupValues[3].trim()
                }
            }
        }

        if (shipToCustomer.isNullOrBlank()) {
            shipToCustomer = "FRESENIUS MEDICAL CARE NORTH AMERICA"
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

    private fun parseItems(lines: List<String>) =
        buildList {
            val seen = mutableSetOf<String>()

            for (i in lines.indices) {
                val rawCurrent = lines[i].trim()
                val collapsedCurrent = collapseDoubledChars(rawCurrent)
                val currentVariants = listOf(
                    normalizeForMatch(rawCurrent),
                    normalizeForMatch(collapsedCurrent)
                )

                val materialMatch = currentVariants.firstNotNullOfOrNull { value ->
                    Regex(
                        """YOUR\s*MATERIAL\s*(?:NUMBER|NO)\s*:?\s*([A-Z0-9./-]+)""",
                        RegexOption.IGNORE_CASE
                    ).find(value)
                } ?: continue

                val sku = normalizeSku(materialMatch.groupValues[1].trim())

                var quantity: Double? = null
                var unitPrice: Double? = null
                var description: String? = null

                for (j in (i - 1) downTo maxOf(0, i - 8)) {
                    val rawCandidate = lines[j].trim()
                    val collapsedCandidate = collapseDoubledChars(rawCandidate)
                    val candidateVariants = listOf(
                        normalizeForMatch(rawCandidate),
                        normalizeForMatch(collapsedCandidate)
                    )

                    if (description == null) {
                        val descCandidate = candidateVariants.firstOrNull { looksLikeDescriptionLine(it) }
                        if (!descCandidate.isNullOrBlank()) {
                            description = descCandidate
                        }
                    }

                    if (quantity == null || unitPrice == null) {
                        for (candidate in candidateVariants) {
                            val numericMatch = Regex(
                                """(?:\d+\s+)?([A-Z0-9./-]+)\s+(\d+(?:\.\d+)?)\s+([A-Z]{2,10})\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""",
                                RegexOption.IGNORE_CASE
                            ).find(candidate)

                            if (numericMatch != null) {
                                quantity = numericMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                                unitPrice = numericMatch.groupValues[4].replace(",", "").toDoubleOrNull()
                                break
                            }

                            val fallbackMatch = Regex(
                                """(?:\d+\s+)?(\d+(?:\.\d+)?)\s+([A-Z]{2,10})\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""",
                                RegexOption.IGNORE_CASE
                            ).find(candidate)

                            if (fallbackMatch != null) {
                                quantity = fallbackMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                                unitPrice = fallbackMatch.groupValues[3].replace(",", "").toDoubleOrNull()
                                break
                            }
                        }
                    }

                    if (description != null && quantity != null && unitPrice != null) {
                        break
                    }
                }

                if (quantity == null || unitPrice == null) continue

                val finalDescription = ItemMapper.getItemDescription(sku).ifBlank {
                    description?.ifBlank { sku } ?: sku
                }

                val key = "$sku|$quantity|$unitPrice"
                if (!seen.add(key)) continue

                add(
                    item(
                        sku = sku,
                        description = finalDescription,
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

    private fun looksLikeDescriptionLine(value: String): Boolean {
        if (value.isBlank()) return false

        val blockedPrefixes = listOf(
            "PURCHASE ORDER",
            "BILL TO",
            "ORDER DATE",
            "COMPANY CODE",
            "FRESENIUS MEDICAL CARE NORTH AMERICA",
            "FREIGHT TERMS",
            "PURCHASING:",
            "VENDOR:",
            "SHIP TO:",
            "DELIVERY DATE",
            "SPECIAL INSTRUCTIONS",
            "YOUR MATERIAL NUMBER",
            "SYSTEM:",
            "EFFECTIVE DATE",
            "SUPPLIER CODE OF CONDUCT"
        )

        if (blockedPrefixes.any { value.startsWith(it) }) return false
        if (value.contains("AIRPARK ROAD")) return false
        if (value.contains("COTTONWOOD")) return false
        if (value.contains("WINTER ST")) return false
        if (value.contains("WALTHAM")) return false
        if (value.contains("ROYAL LANE")) return false
        if (value.contains("DFW AIRPORT")) return false

        val numericRow = Regex(
            """(?:\d+\s+)?[A-Z0-9./-]+\s+\d+(?:\.\d+)?\s+[A-Z]{2,10}\s+\d+(?:\.\d+)?\s+\d+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )

        if (numericRow.matches(value)) return false
        if (Regex("""^\d{10}$""").matches(value)) return false

        return true
    }

    private fun normalizeForMatch(value: String): String {
        return value.uppercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun collapseDoubledChars(value: String): String {
        val s = value.uppercase()
        val out = StringBuilder()
        var i = 0

        while (i < s.length) {
            if (i + 1 < s.length && s[i] == s[i + 1]) {
                out.append(s[i])
                i += 2
            } else {
                out.append(s[i])
                i += 1
            }
        }

        return out.toString()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}