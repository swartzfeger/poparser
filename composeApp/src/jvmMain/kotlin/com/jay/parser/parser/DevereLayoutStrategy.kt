package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DevereLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Devere Company"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("DEVERECOMPANYINC") &&
                text.contains("VENDORNUMBERPRECLAB") &&
                text.contains("JANESVILLEWI53546")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("DEVERECOMPANYINC")) score += 160
        if (text.contains("VENDORNUMBERPRECLAB")) score += 100
        if (text.contains("JANESVILLEWI53546")) score += 80
        if (text.contains("PURCHASINGDEVERECHEMICALCOM")) score += 60
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)

        return ParsedPdfFields(
            customerName = "DEVERE",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "DEVERE COMPANY, INC.",
            addressLine1 = "1923 BELOIT AVENUE",
            addressLine2 = null,
            city = "JANESVILLE",
            state = "WI",
            zip = "53546",
            terms = "1% 10 DAYS NET 30",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? = ORDER_NUMBER_PATTERN
        .find(lines.joinToString(" "))
        ?.groupValues
        ?.get(1)

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_PATTERN.find(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val unitPrice = match.groupValues[5].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku),
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun cleanLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """P\.?\s*O\.?\s*Number:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_PATTERN = Regex(
            """^([A-Z0-9-]+)\s+[A-Z0-9-]+\s+EA\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
