package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class Hand2MindLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Hand2Mind"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(normalizeLines(lines).joinToString("\n"))
        return text.contains("PURCHASEINQUIRY") &&
                text.contains("HAND2MIND") &&
                text.contains("500GREENVIEWCOURT") &&
                text.contains("EXTERNALITEMNUMBER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(normalizeLines(lines).joinToString("\n"))
        var score = 0
        if (text.contains("PURCHASEINQUIRY")) score += 200
        if (text.contains("HAND2MIND")) score += 190
        if (text.contains("500GREENVIEWCOURT")) score += 170
        if (text.contains("VERNONHILLSIL60061")) score += 150
        if (text.contains("H2MPURCHASINGHAND2MINDCOM")) score += 130
        if (text.contains("EXTERNALITEMNUMBER")) score += 110
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = normalizeLines(lines)
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
            },
            shipToCustomer = "HAND2MIND",
            addressLine1 = "500 Greenview Court",
            city = "Vernon Hills",
            state = "IL",
            zip = "60061",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { skuIndex, line ->
            val sku = EXTERNAL_ITEM_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
                ?: return@forEachIndexed
            val row = ((skuIndex - 1) downTo maxOf(0, skuIndex - 7))
                .firstNotNullOfOrNull { candidateIndex -> ITEM_ROW_PATTERN.find(lines[candidateIndex]) }
                ?: return@forEachIndexed
            val quantity = parseNumber(row.groupValues[1]) ?: return@forEachIndexed
            val unitPrice = parseNumber(row.groupValues[2]) ?: return@forEachIndexed

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun normalizeLines(lines: List<String>): List<String> = nonBlankLines(lines).map { line ->
        collapseDoubledGlyphs(line).replace(Regex("""\s+"""), " ").trim()
    }

    private fun collapseDoubledGlyphs(line: String): String {
        val visible = line.filterNot { it.isWhitespace() }
        if (visible.length < 8) return line

        val pairs = visible.chunked(2).filter { it.length == 2 }
        val doubledRatio = pairs.count { it[0] == it[1] }.toDouble() / pairs.size
        if (doubledRatio < DOUBLED_GLYPH_THRESHOLD) return line

        return buildString {
            var index = 0
            while (index < line.length) {
                append(line[index])
                index += if (index + 1 < line.length && line[index] == line[index + 1]) 2 else 1
            }
        }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "HAND2MIND"
        const val DOUBLED_GLYPH_THRESHOLD = 0.70

        val ORDER_NUMBER_PATTERN = Regex("""\bPO-H\d{9}\b""", RegexOption.IGNORE_CASE)
        val EXTERNAL_ITEM_PATTERN = Regex(
            """External\s*item\s*number\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """\b\d{1,2}/\d{1,2}/\d{4}\s+([\d,]+(?:\.\d+)?)\s+EA\s+([\d,]+(?:\.\d+)?)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
