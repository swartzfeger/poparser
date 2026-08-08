package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ProwestSuppliesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Prowest Supplies"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("PROWESTSUPPLIESINC") &&
                text.contains("BAY1460413THSTNE") &&
                text.contains("CALGARYABT2E6P1") &&
                text.contains("PH4032501212")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("PROWESTSUPPLIESINC")) score += 180
        if (text.contains("BAY1460413THSTNE")) score += 160
        if (text.contains("CALGARYABT2E6P1")) score += 140
        if (text.contains("PH4032501212")) score += 120
        if (text.contains("DESCRIPTIONQTYRATEUMAMOUNT")) score += 100
        if (text.contains("89R0V0")) score += 80
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
            shipToCustomer = "PROWEST Supplies Inc.",
            addressLine1 = "Bay #1, 4604 - 13th St. N.E.",
            city = "Calgary",
            state = "AB",
            zip = "T2E 6P1",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = resolveSku(match.groupValues[1]) ?: return@mapNotNull null
        val quantity = parseNumber(match.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[3]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun resolveSku(value: String): String? {
        val normalized = value.uppercase()
        return ItemMapper.getAllSkus()
            .filter { sku -> normalized.startsWith(sku.uppercase()) }
            .maxByOrNull { it.length }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "PROWEST SUPPLIES INC"

        val ORDER_NUMBER_PATTERN = Regex(
            """\b\d{1,2}/\d{1,2}/\d{4}\s+(\d{5,6})\b"""
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\[?([A-Z0-9]+(?:-[A-Z0-9]+)+)]?\s*.+?\s+(\d+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
