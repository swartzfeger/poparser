package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class WilkemScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Wilkem Scientific"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WILKEMSCIENTIFIC") &&
                text.contains("20FACTORYSTREET") &&
                text.contains("FEDID050421622") &&
                text.contains("PHONE4017231840") &&
                text.contains("CATALOGNODESCRIPTIONUPCQTYUOMUNIT")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WILKEMSCIENTIFIC")) score += 200
        if (text.contains("20FACTORYSTREET")) score += 180
        if (text.contains("WESTWARWICKRI02893")) score += 160
        if (text.contains("FEDID050421622")) score += 140
        if (text.contains("PHONE4017231840")) score += 120
        if (text.contains("CATALOGNODESCRIPTIONUPCQTYUOMUNIT")) score += 100
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Wilkem Scientific",
            addressLine1 = "20 Factory Street",
            city = "West Warwick",
            state = "RI",
            zip = "02893",
            terms = clean.firstNotNullOfOrNull { line ->
                TERMS_PATTERN.find(line)?.groupValues?.get(1)?.trim()
            },
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[2].uppercase()
        val quantity = parseNumber(match.groupValues[3]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[5]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank {
                match.groupValues[1].trim()
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
        const val CUSTOMER_ID = "WILKEM SCIENTIFIC"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPURCHASE\s+ORDER\s+(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val TERMS_PATTERN = Regex(
            """\bTERMS\s*:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^LPL[A-Z0-9-]+\s+(.+?)\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+([\d,]+(?:\.\d+)?)\s+(EACH|PACK)\s+(\$?[\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
