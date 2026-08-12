package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class GeorgesIncLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "George's Inc."

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("REPORTLISTINGPOI9") &&
                text.contains("GEORGESPROCUREMENTCO3") &&
                text.contains("VENDOR33006") &&
                text.contains("CASSVILLEMRO") &&
                text.contains("VENDORITEMNUMBER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("GEORGESPROCUREMENTCO3")) score += 220
        if (text.contains("VENDOR33006")) score += 190
        if (text.contains("CASSVILLEMRO")) score += 170
        if (text.contains("HOLLYHUSEGEORGESINCCOM")) score += 150
        if (text.contains("VENDORITEMNUMBER")) score += 130
        if (text.contains("REPORTLISTINGPOI9")) score += 110
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
            shipToCustomer = "Cassville MRO",
            addressLine1 = "9066 State Highway W",
            city = "Cassville",
            state = "MO",
            zip = "65625",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            val sku = VENDOR_ITEM_PATTERN.find(line)
                ?.groupValues
                ?.get(1)
                ?.uppercase()
                ?: return@forEachIndexed
            if (!seen.add(sku)) return@forEachIndexed

            val itemRow = (index - 1 downTo maxOf(0, index - 4))
                .firstNotNullOfOrNull { previousIndex ->
                    ITEM_ROW_PATTERN.matchEntire(lines[previousIndex])
                }
                ?: return@forEachIndexed
            val quantity = parseNumber(itemRow.groupValues[1]) ?: return@forEachIndexed
            val extendedAmount = parseNumber(itemRow.groupValues[2]) ?: return@forEachIndexed
            val unitPrice = extendedAmount.takeIf { quantity > 0.0 }?.div(quantity)

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

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "GEORGES INC"

        val ORDER_NUMBER_PATTERN = Regex(
            """PURCHASE\s+ORDER\s*:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val VENDOR_ITEM_PATTERN = Regex(
            """VENDOR\s+ITEM\s+NUMBER\s*:\s*([A-Z0-9]+(?:-[A-Z0-9]+){2,})""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+.+?\s+([\d,]+(?:\.\d+)?)\s+[A-Z]+\s+([\d,]+(?:\.\d+)?)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
