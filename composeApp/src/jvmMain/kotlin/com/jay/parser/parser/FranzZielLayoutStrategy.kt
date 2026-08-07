package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class FranzZielLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Franz Ziel GmbH"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("FRANZZIELGMBHRAIFFEISENSTR3348727BILLERBECKGERMANY") &&
                text.contains("SUPPLIERNO510128") &&
                text.contains("CONFIRMATIONZIELGMBHCOM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("FRANZZIELGMBHRAIFFEISENSTR3348727BILLERBECKGERMANY")) score += 160
        if (text.contains("SUPPLIERNO510128")) score += 140
        if (text.contains("CONFIRMATIONZIELGMBHCOM")) score += 120
        if (text.contains("YOURARTICLENO")) score += 80
        if (text.contains("JOSEFSUWELACKSTR20")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "FRANZ ZIEL GmbH",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Franz Ziel GmbH",
            addressLine1 = "Josef-Suwelack-Str. 20",
            addressLine2 = "Germany",
            city = "Billerbeck",
            zip = "48727",
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { skuIndex, line ->
            val sku = YOUR_ARTICLE_PATTERN.find(line)?.groupValues?.get(1)?.uppercase() ?: return@forEachIndexed
            val row = ((skuIndex - 1) downTo maxOf(0, skuIndex - 8))
                .firstNotNullOfOrNull { ITEM_ROW_PATTERN.find(lines[it]) }
                ?: return@forEachIndexed
            val quantity = parseEuropeanNumber(row.groupValues[1]) ?: return@forEachIndexed
            val unitPrice = parseEuropeanNumber(row.groupValues[2]) ?: return@forEachIndexed

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

    private fun parseTerms(lines: List<String>): String? = lines.firstNotNullOfOrNull { line ->
        TERMS_PATTERN.find(line)?.groupValues?.get(1)?.trim()?.let { value ->
            when {
                value.equals("Advance payment", ignoreCase = true) -> "Advance Payment"
                value.equals("30 days net", ignoreCase = true) -> "Net 30 Days"
                else -> value
            }
        }
    }

    private fun parseEuropeanNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """ORDER\s*NO\.\s*(\d+)\s*/\s*\d+""",
            RegexOption.IGNORE_CASE
        )
        val YOUR_ARTICLE_PATTERN = Regex(
            """YOUR\s*ARTICLE\s*NO\.\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+\d+\s+([\d.]+,\d{2})\s+[A-Z]+\s+([\d.]+,\d{2})\s+[\d.]+,\d{2}\s+USD$""",
            RegexOption.IGNORE_CASE
        )
        val TERMS_PATTERN = Regex(
            """TERMS\s*OF\s*PAYMENT:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
