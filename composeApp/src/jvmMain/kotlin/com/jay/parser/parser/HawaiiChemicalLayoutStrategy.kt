package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.round

class HawaiiChemicalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Hawaii Chemical & Scientific"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("FEDID990077762") &&
                text.contains("HAWAIISCIENTIFICCOM") &&
                text.contains("SUPPLIERACCOUNT143500PO") &&
                text.contains("2363NKINGSTREET")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("FEDID990077762")) score += 200
        if (text.contains("HAWAIISCIENTIFICCOM")) score += 180
        if (text.contains("SUPPLIERACCOUNT143500PO")) score += 160
        if (text.contains("2363NKINGSTREET")) score += 140
        if (text.contains("HONOLULUHI96819")) score += 120
        if (text.contains("PACKQTYSUPPLIERCATALOGDESCRIPTION")) score += 100
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val shipToOrder = clean.firstNotNullOfOrNull { line ->
            SHIP_TO_ORDER_PATTERN.find(line)
        }

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = shipToOrder?.groupValues?.get(2),
            shipToCustomer = "Hawaii Chemical & Scientific",
            addressLine1 = "2363 N. King Street",
            addressLine2 = shipToOrder?.let { match ->
                if (match.groupValues[1].isNotBlank()) {
                    "HCS PO ${match.groupValues[2]}"
                } else {
                    "PO ${match.groupValues[2]}"
                }
            },
            city = "Honolulu",
            state = "HI",
            zip = "96819",
            terms = if (compact(clean.joinToString(" ")).contains("NET30DAYS")) {
                "Net 30 Days"
            } else {
                null
            },
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val skuMatch = SUPPLIER_SKU_PATTERN.find(line) ?: return@mapNotNull null
        val moneyValues = MONEY_PATTERN.findAll(line)
            .mapNotNull { match -> parseNumber(match.value) }
            .toList()
        if (moneyValues.size < 2) return@mapNotNull null

        val unitPrice = moneyValues[moneyValues.lastIndex - 1]
        val extension = moneyValues.last()
        if (unitPrice <= 0.0 || extension <= 0.0) return@mapNotNull null

        val visibleQuantity = line
            .substring(0, skuMatch.range.first)
            .let { prefix -> LEADING_QUANTITY_PATTERN.find(prefix)?.groupValues?.get(1) }
            ?.toDoubleOrNull()
        val calculatedQuantity = extension / unitPrice
        val quantity = visibleQuantity ?: round(calculatedQuantity)
        if (quantity <= 0.0) return@mapNotNull null

        val sku = skuMatch.groupValues[1].uppercase()
        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "HAWAII CHEMICAL & SC"

        val SHIP_TO_ORDER_PATTERN = Regex(
            """\b(HCS\s+)?PO\s*(\d{5})\b""",
            RegexOption.IGNORE_CASE
        )
        val SUPPLIER_SKU_PATTERN = Regex(
            """\bJG\s*[A-Z0-9]+\s*/\s*([A-Z0-9]+(?:-[A-Z0-9]+){2,})\b""",
            RegexOption.IGNORE_CASE
        )
        val LEADING_QUANTITY_PATTERN = Regex("""^\s*(\d+)\b""")
        val MONEY_PATTERN = Regex("""\b\d[\d,]*\.\d{2}\b""")
    }
}
