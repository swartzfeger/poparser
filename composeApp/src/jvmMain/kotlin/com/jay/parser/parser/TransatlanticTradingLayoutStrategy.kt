package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class TransatlanticTradingLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Transatlantic Trading"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("TRANSATLANTICTRADINGENTERPRISEINC") &&
                text.contains("420ROUTE46EASTSUITE9") &&
                text.contains("VENDORIDPT") &&
                text.contains("UPSACCOUNTR200V8") &&
                text.contains("PARTNUMBERDESCRIPTIONUNITPRICEQTYLINETOTAL")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("TRANSATLANTICTRADINGENTERPRISEINC")) score += 200
        if (text.contains("420ROUTE46EASTSUITE9")) score += 180
        if (text.contains("FAIRFIELDNJ07004")) score += 160
        if (text.contains("VENDORIDPT")) score += 140
        if (text.contains("UPSACCOUNTR200V8")) score += 120
        if (text.contains("PARTNUMBERDESCRIPTIONUNITPRICEQTYLINETOTAL")) score += 100
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.let { "TA$it" }
            },
            shipToCustomer = "Trans Atlantic Trading Enterprise, Inc.",
            addressLine1 = "420 Route 46 East Suite 9",
            city = "Fairfield",
            state = "NJ",
            zip = "07004",
            terms = clean.firstNotNullOfOrNull { line ->
                PAYMENT_TERMS_PATTERN.find(line)?.groupValues?.get(1)?.let { "$it Days" }
            },
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val unitPrice = parseNumber(match.groupValues[3]) ?: return@mapNotNull null
        val quantity = parseNumber(match.groupValues[4]) ?: return@mapNotNull null

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
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "TRANSATLANTIC TRADIN"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bTA\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val PAYMENT_TERMS_PATTERN = Regex(
            """\b(\d+)\s*DAYS\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+(.+?)\s+(\$?[\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
