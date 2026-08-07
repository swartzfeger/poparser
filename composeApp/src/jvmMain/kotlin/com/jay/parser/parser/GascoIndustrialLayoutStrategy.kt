package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class GascoIndustrialLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Gasco Industrial"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WWWGASCOINDUSTRIALCOM") &&
                text.contains("GURABOPR00778") &&
                text.contains("ESTIMADOSUPLIDOR")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WWWGASCOINDUSTRIALCOM")) score += 160
        if (text.contains("GURABOPR00778")) score += 140
        if (text.contains("ESTIMADOSUPLIDOR")) score += 120
        if (text.contains("RINCONINDUSTRIALPARK")) score += 80
        if (text.contains("REQTERMS")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "GASCO INDUSTRIAL",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = "Gasco Industrial Corp.",
            addressLine1 = "Calle A, Lote 3",
            addressLine2 = "Rincon Industrial Park",
            city = "Gurabo",
            state = "PR",
            zip = "00778",
            terms = clean.firstNotNullOfOrNull { line ->
                TERMS_PATTERN.find(line)?.groupValues?.get(1)?.let { "Net ${it.toInt()} Days" }
            },
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.find(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val unitPrice = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """\b\d{1,2}/\d{1,2}/\d{4}\s+(PO\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val TERMS_PATTERN = Regex("""\bNET\s+(\d+)\b""", RegexOption.IGNORE_CASE)
        val ITEM_ROW_PATTERN = Regex(
            """\b(\d{3}[A-Z0-9]*(?:-[A-Z0-9]+)+)\b.*\s([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
