package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class LitRefrigerationLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Lit Refrigeration"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("LIT") &&
                text.contains("1991CORPORATEAVE") &&
                text.contains("LITAUSTINPEAY") &&
                text.contains("3292AUSTINPEAYHWY") &&
                text.contains("VENDORSITEM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("1991CORPORATEAVE")) score += 180
        if (text.contains("LITAUSTINPEAY")) score += 170
        if (text.contains("3292AUSTINPEAYHWY")) score += 160
        if (text.contains("9015278445")) score += 140
        if (text.contains("LOCATIONJR3")) score += 120
        if (text.contains("VENDORSITEM")) score += 100
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
            shipToCustomer = "Lit Austin Peay",
            addressLine1 = "3292 Austin Peay Hwy",
            city = "Memphis",
            state = "TN",
            zip = "38128",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@forEachIndexed
            val sku = row.groupValues[1].uppercase()
            if (!seen.add(sku)) return@forEachIndexed

            val quantity = parseNumber(row.groupValues[3]) ?: return@forEachIndexed
            val extendedCost = parseNumber(row.groupValues[5])
            val visibleUnitPrice = lines
                .drop(index + 1)
                .take(2)
                .firstNotNullOfOrNull { candidate ->
                    UNIT_PRICE_PATTERN.find(candidate)
                        ?.groupValues
                        ?.get(1)
                        ?.let(::parseNumber)
                }
            val unitPrice = visibleUnitPrice
                ?: extendedCost?.takeIf { quantity > 0.0 }?.div(quantity)

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        row.groupValues[2].trim()
                    },
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
        const val CUSTOMER_ID = "LIT REFRIGERATION"

        val ORDER_NUMBER_PATTERN = Regex(
            """\b(?:PO|PR)\s*#\s*:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s*([A-Z]+/\d+)\s+([\d,]+\.\d{2})$""",
            RegexOption.IGNORE_CASE
        )
        val UNIT_PRICE_PATTERN = Regex("""([\d,]+\.\d{4})\s*$""")
    }
}
