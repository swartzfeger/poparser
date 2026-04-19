package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SensonicsIntlLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "SENSONICS INTL"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("SENSONICS") &&
                text.contains("PURCHASE ORDER") &&
                text.contains("411 SOUTH BLACK HORSE PIKE") &&
                text.contains("HADDON HEIGHTS, NJ 08035")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("SENSONICS")) score += 120
        if (text.contains("PURCHASE ORDER")) score += 80
        if (text.contains("411 SOUTH BLACK HORSE PIKE")) score += 80
        if (text.contains("HADDON HEIGHTS, NJ 08035")) score += 80
        if (text.contains("PRC-TASTE-KIT-27-V2")) score += 60
        if (text.contains("PRC-TASTE-KIT-53")) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "SENSONICS INTL",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Sensonics",
            addressLine1 = "411 South Black Horse Pike",
            addressLine2 = null,
            city = "Haddon Heights",
            state = "NJ",
            zip = "08035",
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i].trim()

            if (line.contains("P.O.No.", ignoreCase = true) ||
                line.contains("P.O. No.", ignoreCase = true) ||
                line.contains("PO No.", ignoreCase = true)
            ) {
                val next = lines.getOrNull(i + 1)?.trim().orEmpty()

                Regex("""^\d{1,2}/\d{1,2}/\d{4}\s+(\d{2,})$""")
                    .find(next)
                    ?.groupValues
                    ?.get(1)
                    ?.let { return it }

                Regex("""\b(\d{2,})\b""")
                    .findAll(next)
                    .map { it.groupValues[1] }
                    .lastOrNull()
                    ?.let { return it }
            }
        }

        // Fallback for the known Sensonics pattern: "1/23/2026 943"
        lines.forEach { line ->
            Regex("""^\d{1,2}/\d{1,2}/\d{4}\s+(\d{2,})$""")
                .find(line.trim())
                ?.groupValues
                ?.get(1)
                ?.let { return it }
        }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val rowMatch = Regex(
                """^(PRC-TASTE-KIT-[A-Z0-9-]+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+([A-Z]{2})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (rowMatch != null) {
                val rawSku = rowMatch.groupValues[1].trim().uppercase()
                val firstDesc = rowMatch.groupValues[2].trim()
                val quantity = rowMatch.groupValues[3].replace(",", "").toDoubleOrNull()
                val unitPrice = rowMatch.groupValues[5].replace(",", "").toDoubleOrNull()

                val descParts = mutableListOf(firstDesc)
                var j = i + 1

                while (j < lines.size) {
                    val next = lines[j].replace(Regex("""\s+"""), " ").trim()

                    val nextIsRow = Regex(
                        """^PRC-TASTE-KIT-[A-Z0-9-]+\s+.+?\s+\d+(?:\.\d+)?\s+[A-Z]{2}\s+[\d,]+\.\d{2}\s+[\d,]+\.\d{2}$""",
                        RegexOption.IGNORE_CASE
                    ).matches(next)

                    if (nextIsRow ||
                        next.startsWith("TOTAL", true) ||
                        next.startsWith("TERMS", true) ||
                        next.startsWith("PLEASE CALL", true) ||
                        next.startsWith("PURCHASE ORDER", true)
                    ) {
                        break
                    }

                    if (next.isNotBlank()) {
                        descParts.add(next)
                    }

                    j++
                }

                val sku = mapSensonicsSku(rawSku)
                val description = ItemMapper.getItemDescription(sku).ifBlank {
                    cleanupDescription(descParts.joinToString(" "))
                }

                if (quantity != null && unitPrice != null) {
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

    private fun mapSensonicsSku(rawSku: String): String {
        return when (rawSku.uppercase()) {
            "PRC-TASTE-KIT-27-V2" -> "SPC-KITA-1B-27"
            "PRC-TASTE-KIT-53" -> "SPC-KITA-1B-53"
            else -> rawSku.uppercase()
        }
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace("TasteStripKit", "Taste Strip Kit")
            .replace("SEQNumbers", "SEQ Numbers ")
            .replace("SEQ#", "SEQ#")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("SENSONICS, INC.", "SENSONICS")
            .replace("PRECISIONLABORATORIES", "PRECISION LABORATORIES")
            .replace("411SOUTHBLACKHORSEPIKE", "411 SOUTH BLACK HORSE PIKE")
            .replace("HADDONHEIGHTS,NJ 08035", "HADDON HEIGHTS, NJ 08035")
            .replace("P.O.NO.", "P O NO")
            .replace("PURCHASEORDER", "PURCHASE ORDER")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}