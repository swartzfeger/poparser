package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class AdvanceProductsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Advance Products & Systems"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("ADVANCEPRODUCTSSYSTEMSLLC") &&
                text.contains("108ASSETAVE") &&
                text.contains("001014")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("ADVANCEPRODUCTSSYSTEMSLLC")) score += 160
        if (text.contains("108ASSETAVE")) score += 90
        if (text.contains("SCOTTLA70583")) score += 80
        if (text.contains("001014")) score += 70
        if (text.contains("UNIVERSALINDICATOR")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)
        val joined = clean.joinToString(" ")
        val sku = parseSku(joined)
        val quantity = clean.firstNotNullOfOrNull { line ->
            QUANTITY_PATTERN.find(line)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        }

        return ParsedPdfFields(
            customerName = "ADVANCE PRODUCTS & S",
            orderNumber = ORDER_NUMBER_PATTERN.find(joined)?.groupValues?.get(1),
            shipToCustomer = "ADVANCE PRODUCTS & SYSTEMS LLC",
            addressLine1 = "108 ASSET AVE",
            city = "SCOTT",
            state = "LA",
            zip = "70583",
            items = if (sku != null && quantity != null) {
                listOf(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku),
                        quantity = quantity
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun parseSku(text: String): String? {
        val candidate = SKU_PATTERN.find(text)?.value ?: return null
        return when (candidate.uppercase()) {
            "210-10000-1.53" -> "210-1000-1.53"
            else -> candidate.uppercase()
        }
    }

    private fun cleanLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex("""\b(00\d{5})\s*-\s*\d{2}\b""")
        val SKU_PATTERN = Regex("""\b210-1000{1,2}-1\.53\b""", RegexOption.IGNORE_CASE)
        val QUANTITY_PATTERN = Regex(
            """^\d+\s+PH\s*INDICATOR\s+([\d,]+(?:\.\d+)?)\s+EA\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
