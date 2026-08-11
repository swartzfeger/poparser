package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class TaylorTechnologiesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Taylor Technologies"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("TAYLORWATERTECHNOLOGIESLLC") &&
                text.contains("FEDERALTAXID520847008") &&
                text.contains("TAYLORCUSTOMERSERVICEFLUIDRACOM") &&
                text.contains("PURCHASEORDERNO")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("TAYLORWATERTECHNOLOGIESLLC")) score += 180
        if (text.contains("FEDERALTAXID520847008")) score += 160
        if (text.contains("TAYLORCUSTOMERSERVICEFLUIDRACOM")) score += 140
        if (text.contains("31LOVETONCIRCLE")) score += 120
        if (text.contains("LINEORDERQTYPARTNUMBERDESCRIPTION")) score += 100
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
            shipToCustomer = "Taylor Water Technologies LLC",
            addressLine1 = "31 Loveton Circle",
            city = "Sparks",
            state = "MD",
            zip = "21152",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        lines.forEachIndexed { index, line ->
            val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@forEachIndexed
            val quantity = row.groupValues[1].toDoubleOrNull() ?: return@forEachIndexed
            val unitPrice = row.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@forEachIndexed
            val sku = findWrappedSku(lines.drop(index + 1).take(SKU_LOOKAHEAD_LINES)) ?: return@forEachIndexed

            items += item(
                sku = sku,
                description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                quantity = quantity,
                unitPrice = unitPrice
            )
        }

        return items
    }

    private fun findWrappedSku(lines: List<String>): String? {
        for (index in 0 until lines.lastIndex) {
            val firstFragment = FIRST_SKU_FRAGMENT_PATTERN.find(lines[index])?.groupValues?.get(1) ?: continue
            val secondFragment = SECOND_SKU_FRAGMENT_PATTERN.matchEntire(lines[index + 1])?.groupValues?.get(1) ?: continue
            return (firstFragment + secondFragment).uppercase()
        }

        return lines.firstNotNullOfOrNull { line ->
            COMPLETE_SKU_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
        }
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "TAYLOR TECHNOLOGIES"
        const val SKU_LOOKAHEAD_LINES = 4

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPURCHASE\s+ORDER\s+NO\.?\s+(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([\d,]+(?:\.\d+)?)\s+EA\s+\d+\s+INV\s+\$?([\d,]+(?:\.\d+)?)/\d+\s+\$?[\d,]+(?:\.\d+)?\s+(?:NO|YES)$""",
            RegexOption.IGNORE_CASE
        )
        val COMPLETE_SKU_PATTERN = Regex(
            """#?([A-Z0-9]+(?:-[A-Z0-9]+){2,})\b""",
            RegexOption.IGNORE_CASE
        )
        val FIRST_SKU_FRAGMENT_PATTERN = Regex(
            """#([A-Z0-9]+-)\s*$""",
            RegexOption.IGNORE_CASE
        )
        val SECOND_SKU_FRAGMENT_PATTERN = Regex(
            """([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
    }
}
