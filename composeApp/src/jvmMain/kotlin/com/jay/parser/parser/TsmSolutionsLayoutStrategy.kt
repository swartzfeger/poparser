package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class TsmSolutionsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "TSM Solutions"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("TSMMFGSOLUTIONSPLT") &&
                text.contains("LLP0009470LGN") &&
                text.contains("NO26GROUNDFLOORJALANSS2221") &&
                text.contains("47400PETALINGJAYA") &&
                text.contains("BPBPAPERSHEETS")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("TSMMFGSOLUTIONSPLT")) score += 220
        if (text.contains("LLP0009470LGN")) score += 190
        if (text.contains("NO26GROUNDFLOORJALANSS2221")) score += 170
        if (text.contains("47400PETALINGJAYA")) score += 150
        if (text.contains("BPBPAPERSHEETS")) score += 130
        if (text.contains("2951008510")) score += 110
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
            shipToCustomer = "TSM MFG Solutions PLT",
            addressLine1 = "No. 26, Ground Floor, Jalan SS 22/21",
            addressLine2 = "Damansara Jaya, Malaysia",
            city = "Petaling Jaya",
            state = "Selangor",
            zip = "47400",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[2].uppercase()
        val quantity = parseNumber(match.groupValues[1]) ?: return@mapNotNull null
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
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "TSM SOLUTIONS SDN BH"

        val ORDER_NUMBER_PATTERN = Regex(
            """PURCHASE\s+ORDER\s*#\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(\d+(?:\.\d+)?)\s+.+?\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+([\d,]+(?:\.\d+)?)\s+USD\s*[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
