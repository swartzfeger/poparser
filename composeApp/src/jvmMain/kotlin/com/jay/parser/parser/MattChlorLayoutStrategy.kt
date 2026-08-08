package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MattChlorLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Matt-Chlor Inc"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("MATTCHLORINCPURCHASEORDER") &&
                text.contains("WATERTREATMENTSPECIALISTS") &&
                text.contains("WWWMATTCHLORCOM") &&
                text.contains("4107NORTHARDENDRIVE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("MATTCHLORINCPURCHASEORDER")) score += 200
        if (text.contains("WATERTREATMENTSPECIALISTS")) score += 180
        if (text.contains("MATTCHLORCOM")) score += 160
        if (text.contains("4107NORTHARDENDRIVE")) score += 140
        if (text.contains("SRAP40633158")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Matt Chlor, Inc.",
            addressLine1 = "4107 North Arden Drive",
            city = "El Monte",
            state = "CA",
            zip = "91731-1901",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val quantity = parseNumber(match.groupValues[1]) ?: return@mapNotNull null
        val sku = match.groupValues[2].uppercase()
        val unitPrice = parseNumber(match.groupValues[3]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "MATT-CHLOR INC"

        val ORDER_NUMBER_PATTERN = Regex(
            """P\.O\.\s*No\.:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([\d,]+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+\s+\$?([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
