package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class UnipakLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "UNIPAK"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("UNIPAK LLC") &&
                text.contains("P O NUMBER") &&
                text.contains("2501 PLANTSIDE DR") &&
                text.contains("LOUISVILLE, KY 40299")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("UNIPAK LLC")) score += 120
        if (text.contains("P O NUMBER")) score += 80
        if (text.contains("2501 PLANTSIDE DR")) score += 70
        if (text.contains("LOUISVILLE, KY 40299")) score += 70
        if (text.contains("ITEM CODE")) score += 30
        if (text.contains("ITEM # 345-1-10")) score += 50
        if (text.contains("1%10, NET 30")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "UNIPAK LLC",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "UNIPAK, LLC",
            addressLine1 = "2501 Plantside Dr",
            addressLine2 = null,
            city = "Louisville",
            state = "KY",
            zip = "40299",
            terms = "1%10, Net 30",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""P\.?\s*O\.?\s*Number:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""UNIPAK\s*LLC\s+P\.?O\.?\.?Number:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^([A-Z0-9-]+)\s+([A-Z]+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d{4})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val itemCode = rowMatch.groupValues[1].trim().uppercase()
            val quantity = rowMatch.groupValues[3].replace(",", "").toDoubleOrNull()
            val unitPrice = rowMatch.groupValues[6].replace(",", "").toDoubleOrNull()

            var description: String? = null
            var sku: String? = null

            for (j in (i + 1)..minOf(i + 4, lines.lastIndex)) {
                val next = lines[j].replace(Regex("""\s+"""), " ").trim()

                if (description == null &&
                    next.isNotBlank() &&
                    !next.startsWith("ITEM #", true) &&
                    !next.startsWith("AUTHORIZED", true) &&
                    !next.startsWith("NET ORDER", true)
                ) {
                    description = next
                }

                Regex("""ITEM\s*#\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
                    .find(next)
                    ?.groupValues
                    ?.get(1)
                    ?.let { sku = it.uppercase() }
            }

            if (sku.isNullOrBlank()) {
                sku = when (itemCode) {
                    "02040" -> "345-1-10"
                    else -> null
                }
            }

            if (sku != null && quantity != null && unitPrice != null) {
                val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank {
                    cleanupDescription(description ?: itemCode)
                }

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
        }

        return items
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace("SATestStrips", "SA Test Strips")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("UNIPAKLLC", "UNIPAK LLC")
            .replace("UNIPAK,LLC", "UNIPAK LLC")
            .replace("P.O.NUMBER", "P O NUMBER")
            .replace("P.O. NUMBER", "P O NUMBER")
            .replace("2501PLANTSIDEDR", "2501 PLANTSIDE DR")
            .replace("LOUISVILLE,KY 40299", "LOUISVILLE, KY 40299")
            .replace("ITEM#345-1-10", "ITEM # 345-1-10")
            .replace("1%10,NET30", "1%10, NET 30")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}