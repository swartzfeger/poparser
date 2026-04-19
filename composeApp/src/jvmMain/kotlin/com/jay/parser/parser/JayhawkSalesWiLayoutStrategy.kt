package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class JayhawkSalesWiLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "JAYHAWK SALES WI"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("JAYHAWK SALES WI") &&
                text.contains("JAYHAWK SALES MIDWEST") &&
                text.contains("2995 S MOORLAND RD") &&
                text.contains("NEW BERLIN, WI 53151")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("JAYHAWK SALES WI")) score += 120
        if (text.contains("JAYHAWK SALES MIDWEST")) score += 100
        if (text.contains("2995 S MOORLAND RD")) score += 80
        if (text.contains("NEW BERLIN, WI 53151")) score += 80
        if (text.contains("NET 30")) score += 30
        if (text.contains("BEST WAY")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.trim() }

        return ParsedPdfFields(
            customerName = "JAYHAWK SALES WI",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Jayhawk Sales Midwest",
            addressLine1 = "2995 S Moorland Rd",
            addressLine2 = null,
            city = "New Berlin",
            state = "WI",
            zip = "53151",
            terms = "Net 30",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""P\.?\s*O\.?\s*NO\.?\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(\d{4})\b""")
            .findAll(joined)
            .map { it.groupValues[1] }
            .firstOrNull()
            ?.let { return it }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^([\d,]+)\s+([A-Z0-9-]+)\s+([A-Z0-9-]+)\s+(.+?)\s+([A-Z]+)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (rowMatch != null) {
                val quantity = rowMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                val itemCode = rowMatch.groupValues[2].trim().uppercase()
                val rawSku = rowMatch.groupValues[3].trim().uppercase()
                val firstDesc = rowMatch.groupValues[4].trim()
                val unitPrice = rowMatch.groupValues[6].replace(",", "").toDoubleOrNull()

                val descParts = mutableListOf(firstDesc)
                var j = i + 1

                while (j < lines.size) {
                    val next = lines[j].replace(Regex("""\s+"""), " ").trim()

                    val nextIsRow = Regex(
                        """^[\d,]+\s+[A-Z0-9-]+\s+[A-Z0-9-]+\s+.+\s+[A-Z]+\s+[\d,]+\.\d{2}\s+[\d,]+\.\d{2}$""",
                        RegexOption.IGNORE_CASE
                    ).matches(next)

                    if (nextIsRow ||
                        next.startsWith("Phone#", true) ||
                        next.startsWith("Phone #", true) ||
                        next.startsWith("Total", true) ||
                        next.startsWith("$")
                    ) {
                        break
                    }

                    if (next.isNotBlank()) {
                        descParts.add(next)
                    }

                    j++
                }

                val sku = repairSku(rawSku, itemCode)
                if (quantity != null && unitPrice != null && sku != null) {
                    val description = ItemMapper.getItemDescription(sku).ifBlank {
                        cleanupDescription(descParts.joinToString(" "))
                    }

                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = description,
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

    private fun repairSku(rawSku: String, itemCode: String?): String? {
        val sku = rawSku
            .replace(" ", "")
            .replace("|", "")
            .uppercase()

        return when {
            sku.isBlank() && itemCode == "CT-100" -> "145-144V-100"
            else -> sku.ifBlank { null }
        }
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace("JG-145", "")
            .replace("PRIVATELABELED", "PRIVATE LABELED")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("JAYHAWKSALESMIDWEST", "JAYHAWK SALES MIDWEST")
            .replace("JAYHAWK SALES WI", "JAYHAWK SALES WI")
            .replace("P.O.NO.", "P O NO")
            .replace("P.O. NO.", "P O NO")
            .replace("NEWBERLIN,WI53151", "NEW BERLIN, WI 53151")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}