package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ApothecaryProductsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Apothecary Products"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("APOTHECARYPRODUCTSLLC") &&
                text.contains("VENDORID23325") &&
                text.contains("SHIPTOAPOTHECARYPRODUCTSBURNSVILLEMN")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("APOTHECARYPRODUCTSLLC")) score += 160
        if (text.contains("VENDORID23325")) score += 140
        if (text.contains("SHIPTOAPOTHECARYPRODUCTSBURNSVILLEMN")) score += 100
        if (text.contains("YOURITEM")) score += 60
        if (text.contains("APOTHECARYPRODUCTSMUSTAPPROVE")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "APOTHECARY PRODUCTS",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "APOTHECARY PRODUCTS - BURNSVILLE MN",
            addressLine1 = "11750 12th Avenue South",
            city = "Burnsville",
            state = "MN",
            zip = "55337",
            terms = "1% 10, Net 30 Days",
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { skuIndex, line ->
            val sku = YOUR_ITEM_PATTERN.find(line)?.groupValues?.get(1)?.uppercase() ?: return@forEachIndexed
            val row = ((skuIndex - 1) downTo maxOf(0, skuIndex - 5))
                .firstNotNullOfOrNull { ITEM_ROW_PATTERN.find(lines[it]) }
                ?: return@forEachIndexed
            val quantity = row.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEachIndexed
            val unitPrice = row.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@forEachIndexed

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

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """PURCHASE\s*ORDER\s*NO\s*:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val YOUR_ITEM_PATTERN = Regex(
            """YOUR\s*ITEM\s*:\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+\S+\s+[A-Z]+\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?\s+\d{1,2}/\d{1,2}/\d{4}\s+([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
