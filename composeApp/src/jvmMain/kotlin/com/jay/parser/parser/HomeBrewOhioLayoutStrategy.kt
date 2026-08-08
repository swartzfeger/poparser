package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class HomeBrewOhioLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Home Brew Ohio"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("HOMEBREWOHIO") &&
                text.contains("2333WMONROEST") &&
                text.contains("MIKEHOMEBREWOHIOCOM") &&
                text.contains("ITEMITEMNAMEATTRIBUTESIZEORDRCVDDUECOSTEXTCOST")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("HOMEBREWOHIO")) score += 180
        if (text.contains("2333WMONROEST")) score += 160
        if (text.contains("MIKEHOMEBREWOHIOCOM")) score += 140
        if (text.contains("ACCOUNTHOMEBREWOHIO")) score += 120
        if (text.contains("ITEMITEMNAMEATTRIBUTESIZEORDRCVDDUECOSTEXTCOST")) score += 100
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
            shipToCustomer = "Home Brew Ohio",
            addressLine1 = "2333 W Monroe St",
            city = "Sandusky",
            state = "OH",
            zip = "44870",
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
        const val CUSTOMER_NAME = "HOME BREW OHIO"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPURCHASE\s*ORDER\s*#\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )

        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+.+?\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?\s+[\d,]+(?:\.\d+)?\s+\$?([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
