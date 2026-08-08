package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DiverseyTaiwanLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Diversey Taiwan"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("DIVERSEYHYGIENETAIWANLTD") &&
                text.contains("RUEIGUANGRD") &&
                text.contains("NEIHUDISTTAIPEI") &&
                text.contains("JOHNSONDIVERSEYTAIWAN")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("DIVERSEYHYGIENETAIWANLTD")) score += 220
        if (text.contains("RUEIGUANGRD")) score += 190
        if (text.contains("NEIHUDISTTAIPEI")) score += 170
        if (text.contains("JOHNSONDIVERSEYTAIWAN")) score += 150
        if (text.contains("DHH7P05Z")) score += 130
        if (text.contains("886287514888")) score += 110
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Diversey Hygiene (Taiwan) Ltd.",
            addressLine1 = "4F., No.43, Lane 188, Ruei Guang Rd",
            addressLine2 = "Nei Hu Dist",
            city = "Taipei",
            state = "Taiwan",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val headerIndex = lines.indexOfFirst { compact(it).contains("PONUMBERDATEISSUED") }
        if (headerIndex >= 0) {
            for (index in headerIndex + 1..minOf(headerIndex + 3, lines.lastIndex)) {
                ORDER_ROW_PATTERN.find(lines[index])?.let { return it.groupValues[1] }
            }
        }

        return lines.firstNotNullOfOrNull { line ->
            ORDER_ROW_PATTERN.find(line)?.groupValues?.get(1)
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[2]) ?: return@mapNotNull null
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
        const val CUSTOMER_ID = "DIVERSEY TAIWAN1"

        val ORDER_ROW_PATTERN = Regex("""\b(\d{10})\b""")
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+([\d,]+(?:\.\d+)?)\s+[A-Z]+\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
