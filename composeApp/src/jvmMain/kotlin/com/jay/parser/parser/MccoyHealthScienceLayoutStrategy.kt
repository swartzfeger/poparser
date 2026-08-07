package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MccoyHealthScienceLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "McCoy Health Science"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("MCCOYMEDICAL") &&
                text.contains("IWSPURCHASEORDER") &&
                text.contains("VENDORREFMC710050")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("MCCOYMEDICAL")) score += 160
        if (text.contains("IWSPURCHASEORDER")) score += 140
        if (text.contains("VENDORREFMC710050")) score += 120
        if (text.contains("PURCHASEORDERPMC")) score += 80
        if (text.contains("MARYLANDHEIGHTSMO63043")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "MCCOY MEDICAL",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = "McCoy Medical",
            addressLine1 = "11559 Rock Island Ct",
            city = "Maryland Heights",
            state = "MO",
            zip = "63043",
            terms = "1% 10, Net 30 Days",
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
            """PURCHASE\s*ORDER\s*#\s*(PMC\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+\[([A-Z0-9]+(?:-[A-Z0-9]+)+)]\s*.+?\s+\d{12}\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
