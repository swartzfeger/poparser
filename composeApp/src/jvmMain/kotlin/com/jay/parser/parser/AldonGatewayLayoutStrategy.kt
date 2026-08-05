package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AldonGatewayLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Aldon / Gateway Chemicals"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("GATEWAYCHEMICALSLLC") &&
                text.contains("DBAALDON") &&
                text.contains("VENDORCODEPRELA")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("GATEWAYCHEMICALSLLC")) score += 160
        if (text.contains("DBAALDON")) score += 120
        if (text.contains("221ROCHESTERST")) score += 80
        if (text.contains("VENDORCODEPRELA")) score += 70
        if (text.contains("ALDONCHEMCOM")) score += 50
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)
        return ParsedPdfFields(
            customerName = "ALDON CORPORATION",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "ALDON",
            addressLine1 = "221 ROCHESTER ST",
            city = "AVON",
            state = "NY",
            zip = "14414",
            terms = "NET 30 DAY",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return PO_NUMBER_PATTERN.find(joined)?.groupValues?.get(1)
            ?: PO_FALLBACK_PATTERN.find(joined)?.value
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()
        for (index in lines.indices) {
            val skuMatch = VENDOR_SKU_PATTERN.find(lines[index]) ?: continue
            val sku = normalizeAldonSku(skuMatch.groupValues[1])
            val quantity = ((index - 1) downTo maxOf(0, index - 6))
                .firstNotNullOfOrNull { rowIndex ->
                    ORDER_ROW_PATTERN.find(lines[rowIndex])
                        ?.groupValues
                        ?.get(1)
                        ?.replace(",", "")
                        ?.toDoubleOrNull()
                }
                ?: continue

            if (!seen.add("$sku|$quantity")) continue
            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku),
                    quantity = quantity
                )
            )
        }
    }

    private fun normalizeAldonSku(raw: String): String {
        val normalized = raw.uppercase().trim().replace(".", "-")
        return when (normalized) {
            "SPC-CHROM-500" -> "SPC-CHROM-500-3X4"
            "175-144-100" -> "175-144V-100"
            else -> normalized
        }
    }

    private fun cleanLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val PO_NUMBER_PATTERN = Regex(
            """(?:PO|Pp|[5S][O9])\s*(?:Number|Jumper|umber)?\s*:?\s*(26\d{4})""",
            RegexOption.IGNORE_CASE
        )
        val PO_FALLBACK_PATTERN = Regex("""\b26\d{4}\b""")
        val VENDOR_SKU_PATTERN = Regex(
            """V(?:nd|ind)#:\s*([A-Z0-9.-]+)""",
            RegexOption.IGNORE_CASE
        )
        val ORDER_ROW_PATTERN = Regex(
            """^\d+\s+([\d,]+(?:\.\d+)?)\s+[A-Z0-9-]+\s+.+$""",
            RegexOption.IGNORE_CASE
        )
    }
}
