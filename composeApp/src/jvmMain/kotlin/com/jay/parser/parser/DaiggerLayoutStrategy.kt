package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DaiggerLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Daigger / Weber Scientific"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WEBERSCIENTIFIC") &&
                text.contains("2732KUSERROAD") &&
                text.contains("VENDORIDV06609") &&
                text.contains("1004WHITEHEADRDEXT") &&
                text.contains("ITEMUOMQTYUNITPRICEEXTENDEDPRICE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WEBERSCIENTIFIC")) score += 220
        if (text.contains("2732KUSERROAD")) score += 190
        if (text.contains("VENDORIDV06609")) score += 170
        if (text.contains("1004WHITEHEADRDEXT")) score += 150
        if (text.contains("ITEMUOMQTYUNITPRICEEXTENDEDPRICE")) score += 130
        if (text.contains("ORDERNOPO")) score += 100
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
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = "Weber Scientific",
            addressLine1 = "1004 Whitehead Rd Ext",
            city = "Ewing Township",
            state = "NJ",
            zip = "08638",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[4]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[5]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank {
                match.groupValues[2].trim()
            },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "DAIGGER"

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDER\s*NO\s*\.\s*:\s*(PO\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s*:\s*(.+)\s+([A-Z]+)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
