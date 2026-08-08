package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CommunityLearningLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Community Learning"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("COMMUNITYLEARNINGPURCHASEORDER") &&
                text.contains("600FRANKLINSTREET") &&
                text.contains("PRECISIONLABS") &&
                text.contains("CHROMATOGRAPHYPAPER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("COMMUNITYLEARNINGPURCHASEORDER")) score += 160
        if (text.contains("600FRANKLINSTREETSUITE106")) score += 140
        if (text.contains("SCHENECTADYNEWYORK")) score += 120
        if (text.contains("CHROM506X75")) score += 100
        if (text.contains("9286499833")) score += 80
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
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
            },
            shipToCustomer = "Community Learning",
            addressLine1 = "600 Franklin St",
            addressLine2 = "Suite 106",
            city = "Schenectady",
            state = "NY",
            zip = "12305-2100",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (index in lines.indices) {
            val combined = ITEM_ROW_PATTERN.matchEntire(lines[index])
            val numeric = NUMERIC_ROW_PATTERN.matchEntire(lines[index])

            val sku = combined?.groupValues?.get(1)?.uppercase()
                ?: (maxOf(0, index - 2)..minOf(lines.lastIndex, index + 2))
                    .firstNotNullOfOrNull { candidateIndex ->
                        SKU_PATTERN.find(lines[candidateIndex])?.value?.uppercase()
                    }
                ?: continue
            val displayedQuantity = parseNumber(
                combined?.groupValues?.get(2) ?: numeric?.groupValues?.get(1) ?: continue
            ) ?: continue
            val displayedUnitPrice = parseNumber(
                combined?.groupValues?.get(3) ?: numeric?.groupValues?.get(2) ?: continue
            ) ?: continue

            val isSheetStyle = displayedUnitPrice < 1.0
            val quantity = if (isSheetStyle) displayedQuantity / SHEETS_PER_PACKAGE else displayedQuantity
            val unitPrice = if (isSheetStyle) displayedUnitPrice * SHEETS_PER_PACKAGE else displayedUnitPrice

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
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "COMMUNITY LEARNING"
        const val SHEETS_PER_PACKAGE = 50.0

        val ORDER_NUMBER_PATTERN = Regex("""\bPO-\d+\b""", RegexOption.IGNORE_CASE)
        val ITEM_ROW_PATTERN = Regex(
            """^(CHROM-[A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val NUMERIC_ROW_PATTERN = Regex(
            """^([\d,]+(?:\.\d+)?)\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$"""
        )
        val SKU_PATTERN = Regex("""\bCHROM-[A-Z0-9]+(?:-[A-Z0-9]+)+\b""", RegexOption.IGNORE_CASE)
    }
}
