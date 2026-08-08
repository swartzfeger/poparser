package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ExtractTechnologyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Extract Technology"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("EXTRACTTECHNOLOGYUS") &&
                text.contains("ACCOUNTNUMBERBUYERPODATE") &&
                text.contains("MATNOREQUIRED")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("EXTRACTTECHNOLOGYUS")) score += 160
        if (text.contains("ACCOUNTNUMBERBUYERPODATE")) score += 140
        if (text.contains("PARTNODESCRIPTIONVENDOR")) score += 100
        if (text.contains("MATNOREQUIRED")) score += 40
        if (text.contains("ETAPURCHASINGEXTRACTTECHNOLOGYCOM")) score += 100
        if (text.contains("514300")) score += 80
        if (text.contains("161ENSCHSTREET")) score += 60
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_NAME)

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "EXTRACT TECHNOLOGY US",
            addressLine1 = "161 Ensch Street",
            city = "Mauston",
            state = "WI",
            zip = "53948",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
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
        const val CUSTOMER_NAME = "EXTRACT TECHNOLOGY"
        val ORDER_NUMBER_PATTERN = Regex("""\b(74\d{8})\b""")
        val ITEM_ROW_PATTERN = Regex(
            """^\d{8,12}\s+.+?\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+\d{1,2}/\d{1,2}/\d{2,4}\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s*[A-Z]{1,5}\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
