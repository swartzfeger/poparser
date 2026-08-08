package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class EasternCrownLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Eastern Crown"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("EASTERNCROWNINC") &&
                text.contains("4228PETERBOROROAD") &&
                text.contains("VENDORNO0001140") &&
                text.contains("WAYNEBETSINGER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("EASTERNCROWNINC")) score += 220
        if (text.contains("4228PETERBOROROAD")) score += 190
        if (text.contains("VENDORNO0001140")) score += 170
        if (text.contains("WAYNEBETSINGER")) score += 150
        if (text.contains("PONUMBER")) score += 130
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
            shipToCustomer = "EASTERN CROWN, INC.",
            addressLine1 = "4228 Peterboro Road",
            addressLine2 = "P.O. Box 850",
            city = "Vernon",
            state = "NY",
            zip = "13476",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { rowIndex, line ->
            val row = ITEM_START_PATTERN.matchEntire(line) ?: return@forEachIndexed
            val rowCode = row.groupValues[1].replace('=', '-').uppercase()
            val displayedQuantity = parseNumber(row.groupValues[2]) ?: return@forEachIndexed
            val manufacturerSku = (rowIndex + 1..minOf(rowIndex + 2, lines.lastIndex))
                .firstNotNullOfOrNull { candidateIndex ->
                    MFG_SKU_PATTERN.find(lines[candidateIndex])?.groupValues?.get(1)?.uppercase()
                }

            val displayedUnitPrice = if (row.groupValues[5].isNotBlank()) {
                parseNumber(row.groupValues[5])
            } else {
                (rowIndex + 1..minOf(rowIndex + 8, lines.lastIndex))
                    .firstNotNullOfOrNull { candidateIndex ->
                        SPLIT_NUMERIC_PATTERN.matchEntire(lines[candidateIndex])
                            ?.groupValues
                            ?.get(3)
                            ?.let(::parseNumber)
                    }
            } ?: return@forEachIndexed

            val sku = manufacturerSku ?: rowCode
            val caseDivisor = if (manufacturerSku != null) vialCaseDivisor(sku) else null
            val quantity = caseDivisor?.let { displayedQuantity / it } ?: displayedQuantity
            val unitPrice = caseDivisor?.let { displayedUnitPrice * it } ?: displayedUnitPrice

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

    private fun vialCaseDivisor(sku: String): Double? = sku
        .split("-")
        .firstNotNullOfOrNull { segment ->
            Regex("""^(\d+)V$""").matchEntire(segment)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        ?.takeIf { it > 0.0 }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "EASTERN CROWN"

        val ORDER_NUMBER_PATTERN = Regex(
            """P\.O\.\s*NUMBER:\s*(\d{7})""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_START_PATTERN = Regex(
            """^\*?\s*([A-Z0-9]+(?:[-=][A-Z0-9]+)+)\s+EACH\s+([\d,]+(?:\.\d+)?)(?:\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?))?$""",
            RegexOption.IGNORE_CASE
        )
        val MFG_SKU_PATTERN = Regex(
            """MFG\s*ID#\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
        val SPLIT_NUMERIC_PATTERN = Regex(
            """^([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)$"""
        )
    }
}
