package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class IsssCoLtdLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WWWISSS168ORG") &&
                text.contains("ISSSCOLTD") &&
                text.contains("IMPORTACCOUNT962099063") &&
                text.contains("2202002070") &&
                text.contains("SPC160MIL")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WWWISSS168ORG")) score += 240
        if (text.contains("ISSSCOLTD")) score += 220
        if (text.contains("IMPORTACCOUNT962099063")) score += 200
        if (text.contains("0735561006219")) score += 180
        if (text.contains("2202002070")) score += 160
        if (text.contains("SPC160MIL")) score += 140
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
            shipToCustomer = "ISSS CO., LTD.",
            addressLine1 = "49/173 Moo 10",
            addressLine2 = "Tha Talat, Thailand",
            city = "Sam Phran",
            state = "Nakhon Pathom",
            zip = "73110",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = ITEM_SPECS.mapNotNull { spec ->
        val skuIndex = lines.indexOfFirst { line -> spec.skuPattern.containsMatchIn(line) }
        if (skuIndex < 0) return@mapNotNull null

        val itemText = (skuIndex..minOf(skuIndex + 3, lines.lastIndex))
            .joinToString(" ") { candidateIndex -> lines[candidateIndex] }
        val values = spec.rowPattern.find(itemText) ?: return@mapNotNull null
        val quantity = parseNumber(values.groupValues[1]) ?: return@mapNotNull null
        val unitPrice = parseNumber(values.groupValues[2]) ?: return@mapNotNull null
        val extension = parseNumber(values.groupValues[3]) ?: return@mapNotNull null
        if (quantity <= 0.0 || unitPrice <= 0.0 || extension <= 0.0) return@mapNotNull null

        item(
            sku = spec.sku,
            description = ItemMapper.getItemDescription(spec.sku).ifBlank { spec.sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = spec.uom
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ItemSpec(
        val sku: String,
        val uom: String,
        val skuPattern: Regex,
        val rowPattern: Regex
    )

    private companion object {
        const val CUSTOMER_ID = "ISSS CO LTD"

        val ORDER_NUMBER_PATTERN = Regex("""\bPO\d{6}\b""", RegexOption.IGNORE_CASE)
        val ITEM_SPECS = listOf(
            ItemSpec(
                sku = "220-200-2070",
                uom = "AMB BO",
                skuPattern = Regex("""220-200-2070\b""", RegexOption.IGNORE_CASE),
                rowPattern = Regex(
                    """220-200-2070.*?\b([\d,]+(?:\.\d+)?)\s*/?\s*AMB\s*BO\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\b""",
                    RegexOption.IGNORE_CASE
                )
            ),
            ItemSpec(
                sku = "SPC-160-MIL",
                uom = "100 STR",
                skuPattern = Regex("""SPC-160-MIL\b""", RegexOption.IGNORE_CASE),
                rowPattern = Regex(
                    """SPC-160-MIL.*?\b([\d,]+(?:\.\d+)?)\s+100\s*STR\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\b""",
                    RegexOption.IGNORE_CASE
                )
            )
        )
    }
}
