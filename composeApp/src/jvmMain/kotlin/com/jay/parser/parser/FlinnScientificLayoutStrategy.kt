package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class FlinnScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "FLINN SCIENTIFIC"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("FLINN SCIENTIFIC") &&
                text.contains("PURCHASE ORDER NO") &&
                text.contains("VENDOR #") &&
                text.contains("FLINN #")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("FLINN SCIENTIFIC")) score += 100
        if (text.contains("PURCHASE ORDER NO")) score += 80
        if (text.contains("P.O. BOX 219")) score += 40
        if (text.contains("BATAVIA, IL 60510")) score += 40
        if (text.contains("VENDOR #")) score += 30
        if (text.contains("FLINN #")) score += 30
        if (text.contains("UNIT")) score += 20
        if (text.contains("EXTENDED")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "FLINN SCIENTIFIC, INC.",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Flinn Scientific Inc.",
            addressLine1 = "950 N. Raddant Road",
            addressLine2 = null,
            city = "Batavia",
            state = "IL",
            zip = "60510",
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""PURCHASE ORDER NO\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(44\d{4,})\b""")
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val itemMatch = Regex(
                """^\s*([\d.,O ]+)\s+([A-Z0-9-]+)\s+([A-Z0-9]+)\s+EA\s+([A-Z0-9. ]+)\s+([A-Z0-9. ]+)\s*$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (itemMatch != null) {
                val quantity = parseNumber(itemMatch.groupValues[1])
                val rawSku = itemMatch.groupValues[2].trim().uppercase()
                val unitPrice = parseNumber(itemMatch.groupValues[4])

                val sku = repairSku(rawSku)

                val descLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j].trim()

                    val nextLooksLikeItem = Regex(
                        """^\s*[\d.,O ]+\s+[A-Z0-9-]+\s+[A-Z0-9]+\s+EA\s+[A-Z0-9. ]+\s+[A-Z0-9. ]+\s*$""",
                        RegexOption.IGNORE_CASE
                    ).matches(next)

                    if (next.startsWith("Total:", true) ||
                        next.startsWith("Authorization Signature", true) ||
                        next.startsWith("Important Notes", true) ||
                        nextLooksLikeItem
                    ) {
                        break
                    }

                    if (next.isNotBlank()) {
                        descLines.add(next)
                    }
                    j++
                }

                val rawDescription = descLines.joinToString(" ").trim()
                val cleanedDescription = cleanupDescription(rawDescription)
                val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank {
                    cleanedDescription.ifBlank { null }
                }

                if (quantity != null && unitPrice != null) {
                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = mappedDescription,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                i = j
                continue
            }

            i++
        }

        return items
    }

    private fun parseNumber(raw: String): Double? {
        var normalized = raw
            .uppercase()
            .replace("O", "0")
            .replace(",", ".")
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""[^0-9.]"""), "")

        if (normalized.count { it == '.' } > 1) {
            val first = normalized.indexOf('.')
            normalized = normalized.substring(0, first + 1) + normalized.substring(first + 1).replace(".", "")
        }

        return normalized.toDoubleOrNull()
    }

    private fun repairSku(rawSku: String): String {
        val sku = rawSku
            .replace("O", "0")
            .replace("I", "1")

        return when (sku) {
            "196-144V-100" -> "166-144V-100"
            else -> sku
        }
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace("ANDO", "AND")
            .replace("5SO/VIAL", "50/VIAL")
            .replace("THIQUREA", "THIOUREA")
            .replace("(THIQUREA", "THIOUREA")
            .replace("Ine.", "Inc.")
            .replace("ORIVE", "DRIVE")
            .replace(Regex("""\bCS500\b"""), "")
            .replace(Regex("""\bCS144\b"""), "")
            .replace(Regex("""\bC5500\b"""), "")
            .replace(Regex("""\bC5144\b"""), "")
            .replace(Regex("""\bEA\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("INE.", "INC.")
            .replace("ORIVE", "DRIVE")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}