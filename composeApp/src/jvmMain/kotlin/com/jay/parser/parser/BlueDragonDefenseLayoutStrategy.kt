package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class BlueDragonDefenseLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("BLUEDRAGONDEFENSELLC") &&
                text.contains("1121WHISPERINGDOEDR") &&
                text.contains("WILMINGTONNC28409") &&
                text.contains("POTASSIUMIODIDESTARCHTESTPAPERS") &&
                text.contains("160144V100")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("BLUEDRAGONDEFENSELLC")) score += 240
        if (text.contains("1121WHISPERINGDOEDR")) score += 210
        if (text.contains("WILMINGTONNC28409")) score += 190
        if (text.contains("POTASSIUMIODIDESTARCHTESTPAPERS")) score += 170
        if (text.contains("160144V100")) score += 150
        if (text.contains("FEDEX205241865")) score += 130
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
            shipToCustomer = "Blue Dragon Defense LLC",
            addressLine1 = "1121 Whispering Doe Dr",
            city = "Wilmington",
            state = "NC",
            zip = "28409",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { skuIndex, line ->
            val sku = SKU_PATTERN.find(line)?.value?.uppercase() ?: return@forEachIndexed
            val itemRow = ((skuIndex - 3)..skuIndex)
                .filter { candidateIndex -> candidateIndex in lines.indices }
                .firstNotNullOfOrNull { candidateIndex -> ITEM_VALUES_PATTERN.find(lines[candidateIndex]) }
                ?: return@forEachIndexed
            val quantity = parseNumber(itemRow.groupValues[1]) ?: return@forEachIndexed
            val unitPrice = parseNumber(itemRow.groupValues[2]) ?: return@forEachIndexed
            val extension = parseNumber(itemRow.groupValues[3]) ?: return@forEachIndexed
            if (quantity <= 0.0 || unitPrice <= 0.0 || extension <= 0.0) return@forEachIndexed

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice,
                    uom = "EA"
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
        const val CUSTOMER_ID = "BLUE DRAGON DEFENSE"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bP\.?\s*O\.?\s*(?:#|NO\.?)?\s*:?\s*(\d{3,})\b""",
            RegexOption.IGNORE_CASE
        )
        val SKU_PATTERN = Regex("""\b160-144V-100\b""", RegexOption.IGNORE_CASE)
        val ITEM_VALUES_PATTERN = Regex(
            """\b([\d,]+(?:\.\d+)?)\s+([\d,]+\.\d{4,6})\s+([\d,]+\.\d{2})\b"""
        )
    }
}
