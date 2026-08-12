package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class UnitedScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "United Scientific Supplies"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("UNITEDSCICOM") &&
                text.contains("1001TECHNOLOGYWAY") &&
                text.contains("VENDOR248PRECISION") &&
                text.contains("3630AMHURSTPARKWAY") &&
                text.contains("LINEQTYUOMITEMDESCRIPTIONUNITPRICEEXTENSION")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("UNITEDSCICOM")) score += 220
        if (text.contains("1001TECHNOLOGYWAY")) score += 190
        if (text.contains("VENDOR248PRECISION")) score += 170
        if (text.contains("3630AMHURSTPARKWAY")) score += 150
        if (text.contains("LINEQTYUOMITEMDESCRIPTIONUNITPRICEEXTENSION")) score += 130
        if (text.contains("PURCHASEORDER")) score += 100
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
            shipToCustomer = "United Scientific Supplies, Inc.",
            addressLine1 = "3630 Amhurst Parkway",
            city = "Waukegan",
            state = "IL",
            zip = "60085",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            val sku = VENDOR_SKU_PATTERN.matchEntire(line)
                ?.groupValues
                ?.get(1)
                ?.uppercase()
                ?: return@forEachIndexed
            if (!seen.add(sku)) return@forEachIndexed

            val itemRow = lines.getOrNull(index - 1)
                ?.let { ITEM_ROW_PATTERN.matchEntire(it) }
                ?: return@forEachIndexed
            val quantity = parseNumber(itemRow.groupValues[1]) ?: return@forEachIndexed
            val unitPrice = parseNumber(itemRow.groupValues[2]) ?: return@forEachIndexed

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
        const val CUSTOMER_ID = "UNITED SCIENTIFIC"

        val ORDER_NUMBER_PATTERN = Regex(
            """PO\s*#\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val VENDOR_SKU_PATTERN = Regex(
            """#\s*([A-Z0-9]+(?:-[A-Z0-9]+){2,})""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\.\s+([\d,]+(?:\.\d+)?)\s+[A-Z]+\s+[A-Z0-9-]+\s+.+?\s+\$([\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?\s+\d{1,2}/\d{1,2}/\d{4}$""",
            RegexOption.IGNORE_CASE
        )
    }
}
