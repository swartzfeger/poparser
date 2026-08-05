package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ButlerChemicalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Butler Chemical Products"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("BUTLERCHEMICALSINC") &&
                text.contains("3070ECEENACOURT") &&
                text.contains("PRE500")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("BUTLERCHEMICALSINC")) score += 140
        if (text.contains("3070ECEENACOURT")) score += 90
        if (text.contains("ANAHEIMCA92806")) score += 70
        if (text.contains("PRE500")) score += 60
        if (text.contains("JG145")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)

        return ParsedPdfFields(
            customerName = "BUTLER CHEMICAL PROD",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "BUTLER CHEMICALS INC.",
            addressLine1 = parseAddressLine1(clean),
            addressLine2 = null,
            city = "Anaheim",
            state = "CA",
            zip = "92806",
            terms = clean.firstOrNull { it.contains("Net 30 days", ignoreCase = true) }
                ?.let { "Net 30 days" },
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val dateIndex = lines.indexOfFirst { ORDER_DATE_PATTERN.matches(it) }
        if (dateIndex > 0) {
            lines[dateIndex - 1].takeIf { PO_NUMBER_PATTERN.matches(it) }?.let { return it }
        }
        return lines.firstOrNull(PO_NUMBER_PATTERN::matches)
    }

    private fun parseAddressLine1(lines: List<String>): String? = lines
        .firstNotNullOfOrNull { line ->
            STREET_PATTERN.find(line)?.groupValues?.get(1)
        }
        ?.uppercase()

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        for (index in lines.indices) {
            val row = ORDER_LINE_PATTERN.find(lines[index]) ?: continue
            val quantity = row.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val unitPrice = row.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            val skuLine = lines.getOrNull(index + 1).orEmpty()
            val skuMatch = SKU_LINE_PATTERN.find(skuLine) ?: continue
            val sku = skuMatch.groupValues[1].uppercase()
            if (!seen.add("$sku|$quantity|$unitPrice")) continue

            val poDescription = listOf(
                skuMatch.groupValues[2].trim(),
                lines.getOrNull(index + 2).orEmpty()
            ).filter { it.isNotBlank() }.joinToString(" ")

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { poDescription },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun cleanLine(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val PO_NUMBER_PATTERN = Regex("""^\d{4,10}$""")
        val ORDER_DATE_PATTERN = Regex("""^\d{2}-\d{2}-\d{2}$""")
        val STREET_PATTERN = Regex("""\b(3070\s+E\s+CEENA\s+COURT)\b""", RegexOption.IGNORE_CASE)
        val ORDER_LINE_PATTERN = Regex(
            """^\d+\s+[A-Z0-9-]+\s+\d{2}-\d{2}-\d{2}\s+([\d,]+\.\d+)\s+([\d,]+\.\d+)\s+[\d,]+\.\d+$""",
            RegexOption.IGNORE_CASE
        )
        val SKU_LINE_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s*-\s*(.+?)\s+EA\s+per\s+1\s+Non-Taxable$""",
            RegexOption.IGNORE_CASE
        )
    }
}
