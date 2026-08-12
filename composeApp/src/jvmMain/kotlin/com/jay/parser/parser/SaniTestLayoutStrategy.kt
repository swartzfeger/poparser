package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SaniTestLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "SANI-TEST"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SANITESTLLC") &&
                text.contains("POBOX461") &&
                text.contains("364NINAWAY") &&
                text.contains("WARMINSTERPENNSYLVANIA18974") &&
                text.contains("PURCHASINGSANITESTCOM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SANITESTLLC")) score += 220
        if (text.contains("POBOX461")) score += 190
        if (text.contains("364NINAWAY")) score += 170
        if (text.contains("WARMINSTERPENNSYLVANIA18974")) score += 150
        if (text.contains("PURCHASINGSANITESTCOM")) score += 130
        if (text.contains("HYPOCHLOROUSCONCENTRATIONTESTSTRIPS")) score += 110
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
            shipToCustomer = "Sani-TEST",
            addressLine1 = "364 Nina Way",
            city = "Warminster",
            state = "PA",
            zip = "18974",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val rowText = row.groupValues[1]
        val sku = classifySku(rowText) ?: return@mapNotNull null
        val quantity = parseNumber(row.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(row.groupValues[3]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun classifySku(rowText: String): String? {
        val compactRow = compact(rowText)
        return when {
            compactRow.contains("08S0201") ||
                    (compactRow.contains("010PPM") && compactRow.contains("50COUNTVIAL")) ->
                "CHL-10-1V-50"

            compactRow.contains("08S0204") || compactRow.contains("01000PPM") ->
                "CHL-1000-1V-100"

            compactRow.contains("08S0205") || compactRow.contains("02000PPM") ->
                "CHL-2000-1V-100"

            else -> null
        }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "SANI-TEST"

        val ORDER_NUMBER_PATTERN = Regex(
            """\b(PO-\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(.+?)\s+(\d+(?:\.\d+)?)\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?\s+0$""",
            RegexOption.IGNORE_CASE
        )
    }
}
