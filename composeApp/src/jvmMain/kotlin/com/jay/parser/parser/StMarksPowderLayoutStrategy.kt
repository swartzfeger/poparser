package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class StMarksPowderLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "St. Marks Powder"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("STMARKSPOWDERINC") &&
                text.contains("7121COASTALHWY") &&
                text.contains("PRECISIONLABORATORIESINC")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("STMARKSPOWDERINC")) score += 160
        if (text.contains("7121COASTALHWY")) score += 100
        if (text.contains("CRAWFORDVILLEFL32327")) score += 90
        if (text.contains("PRECISIONLABORATORIESINC")) score += 70
        if (text.contains("VENDOR2192")) score += 50
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)

        return ParsedPdfFields(
            customerName = "ST MARKS POWDER",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "St. Marks Powder, Inc.",
            addressLine1 = "7121 Coastal Hwy.",
            city = "Crawfordville",
            state = "FL",
            zip = "32327",
            terms = clean.firstOrNull { it.contains("NET30", ignoreCase = true) }
                ?.let { "NET30" },
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? = lines
        .firstNotNullOfOrNull { line ->
            ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
        }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        for (index in lines.indices) {
            val row = ORDER_LINE_PATTERN.find(lines[index]) ?: continue
            val quantity = row.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val unitPrice = row.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            val sku = ((index + 1)..minOf(lines.lastIndex, index + 3))
                .firstNotNullOfOrNull { lineIndex -> parseSku(lines[lineIndex]) }
                ?: continue
            if (!seen.add("$sku|$quantity|$unitPrice")) continue

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku),
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun parseSku(line: String): String? {
        val normalized = line.uppercase().replace(Regex("""\s+"""), "")
        val valueAfterMarker = when {
            "P/N" in normalized -> normalized.substringAfter("P/N")
            "#" in normalized -> normalized.substringAfter("#")
            else -> return null
        }

        return ItemMapper.getAllSkus()
            .sortedByDescending { it.length }
            .firstOrNull { valueAfterMarker.startsWith(it.uppercase()) }
            ?: FALLBACK_PART_NUMBER_PATTERN.find(valueAfterMarker)?.value
            ?: FALLBACK_SPC_PATTERN.find(valueAfterMarker)?.value
    }

    private fun cleanLine(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex("""\b(\d{6})\s+\d{2}/\d{2}/\d{2}\b""")
        val ORDER_LINE_PATTERN = Regex(
            """^\d+\s+\*?\s*-\s*([\d,]+(?:\.\d+)?)\s+(?:PK|EA)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val FALLBACK_PART_NUMBER_PATTERN = Regex("""^\d+(?:-\d+)+""")
        val FALLBACK_SPC_PATTERN = Regex("""^SPC-\d+-[A-Z]+""")
    }
}
