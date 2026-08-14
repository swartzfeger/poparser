package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class NaturesWorkshopLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Nature's Workshop"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("NATURESWORKSHOPPLUSINC") &&
                text.contains("3055EMAINSTREET") &&
                text.contains("DANVILLEIN46122") &&
                text.contains("MAILWORKSHOPPLUSCOM") &&
                text.contains("QTYITEMNODESCRIPTIONPRICE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("NATURESWORKSHOPPLUSINC")) score += 220
        if (text.contains("3055EMAINSTREET")) score += 190
        if (text.contains("DANVILLEIN46122")) score += 170
        if (text.contains("MAILWORKSHOPPLUSCOM")) score += 150
        if (text.contains("PURCHASEORDER")) score += 130
        if (text.contains("QTYITEMNODESCRIPTIONPRICE")) score += 110
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
            shipToCustomer = "Nature's Workshop Plus Inc.",
            addressLine1 = "3055 E Main Street",
            city = "Danville",
            state = "IN",
            zip = "46122",
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
        const val CUSTOMER_ID = "NATURES WORKSHOP"

        val ORDER_NUMBER_PATTERN = Regex(
            """PURCHASE\s+ORDER\s*#\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([\d,]+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
