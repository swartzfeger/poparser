package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DwDaviesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "D.W. Davies"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("DWDAVIESCOINC") &&
                text.contains("3200PHILLIPSAVE") &&
                text.contains("RACINEWI53403") &&
                text.contains("P0114") &&
                text.contains("SUPPLIERNUMBER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("DWDAVIESCOINC")) score += 220
        if (text.contains("3200PHILLIPSAVE")) score += 190
        if (text.contains("RACINEWI53403")) score += 170
        if (text.contains("P0114")) score += 150
        if (text.contains("SUPPLIERNUMBER")) score += 130
        if (text.contains("PURCHASEORDER")) score += 110
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
            shipToCustomer = "D.W. Davies & Co., Inc.",
            addressLine1 = "3200 Phillips Ave",
            city = "Racine",
            state = "WI",
            zip = "53403",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        lines.firstOrNull { line -> STANDALONE_ORDER_NUMBER_PATTERN.matches(line) }
            ?.let { return it }

        return lines.firstNotNullOfOrNull { line ->
            OCR_ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { index, line ->
            val supplierMatch = SUPPLIER_NUMBER_PATTERN.find(line) ?: return@forEachIndexed
            val sku = supplierMatch.groupValues[1].uppercase()
            val quantity = parseNumber(supplierMatch.groupValues[2]) ?: return@forEachIndexed
            val productLine = (index - 1 downTo maxOf(0, index - 3))
                .map { previousIndex -> lines[previousIndex] }
                .firstOrNull { previousLine -> UNIT_PRICE_PATTERN.containsMatchIn(previousLine) }
                ?: return@forEachIndexed
            val unitPrice = UNIT_PRICE_PATTERN.find(productLine)
                ?.groupValues
                ?.get(1)
                ?.let(::parseNumber)
                ?: return@forEachIndexed

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
        const val CUSTOMER_ID = "D.W. DAVIES"

        val STANDALONE_ORDER_NUMBER_PATTERN = Regex("""\d{5}""")
        val OCR_ORDER_NUMBER_PATTERN = Regex(
            """SHIP\s+TO\s*:\s*(\d{5})\b""",
            RegexOption.IGNORE_CASE
        )
        val SUPPLIER_NUMBER_PATTERN = Regex(
            """SUPPLIER\s+NUMBER\s*:\s*([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s*-?\s*(\d+(?:\.\d+)?)\s*$""",
            RegexOption.IGNORE_CASE
        )
        val UNIT_PRICE_PATTERN = Regex(
            """USD\s*([\d,]+(?:\.\d+)?)\s*/\s*E\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
