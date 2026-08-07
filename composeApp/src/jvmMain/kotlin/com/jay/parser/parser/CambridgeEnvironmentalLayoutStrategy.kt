package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CambridgeEnvironmentalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Cambridge Environmental"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CAMBRIDGEENVIRONMENTALPRODUCTS") &&
                text.contains("OURACCOUNTCAMBRIDGEENVIRONMEN") &&
                text.contains("GSTREGNOR898410337")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CAMBRIDGEENVIRONMENTALPRODUCTS")) score += 160
        if (text.contains("OURACCOUNTCAMBRIDGEENVIRONMEN")) score += 140
        if (text.contains("GSTREGNOR898410337")) score += 120
        if (text.contains("COBUCKLANDGLOBALTRADE")) score += 80
        if (text.contains("PURCHASINGCEPRODUCTSCA")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "CAMBRIDGE ENVIRONMENTAL PRODUCTS, INC",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.matchEntire(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Cambridge Environmental Products",
            addressLine1 = "1915 Dove Street",
            addressLine2 = "C/O Buckland Global Trade",
            city = "Port Huron",
            state = "MI",
            zip = "48060",
            terms = "Credit Card",
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
        val ORDER_NUMBER_PATTERN = Regex("""^(\d{5})$""")
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+([\d,]+(?:\.\d+)?)\s+PK\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
