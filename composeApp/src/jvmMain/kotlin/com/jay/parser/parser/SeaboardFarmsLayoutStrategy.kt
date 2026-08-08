package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SeaboardFarmsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Seaboard Farms"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SEABOARDFOODS") &&
                text.contains("2700CACTUSDRIVE") &&
                text.contains("GUYMON") &&
                text.contains("PURCHASEORDER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SEABOARDFOODS")) score += 160
        if (text.contains("SUPPLIER366125")) score += 150
        if (text.contains("PRECISIONLABORATORIESINC")) score += 140
        if (text.contains("2700CACTUSDRIVE")) score += 120
        if (text.contains("LEGALANDCOMPLIANCESEABOARDFOODSCOM")) score += 80
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipToName = parseShipToName(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = shipToName,
            addressLine1 = "2700 Cactus Drive",
            city = "Guymon",
            state = "OK",
            zip = "73942",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipToName(lines: List<String>): String {
        val text = compact(lines.joinToString("\n"))
        return when {
            text.contains("GUYMONMAINTENANCE") -> "Guymon Maintenance"
            text.contains("GUYMONPLANT") -> "Guymon Plant"
            else -> "Guymon Plant"
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        for (index in lines.indices) {
            val numeric = ITEM_NUMERIC_PATTERN.find(lines[index]) ?: continue
            val rowDescription = numeric.groupValues[1]
            val quantity = parseNumber(numeric.groupValues[2]) ?: continue
            val unitPrice = parseNumber(numeric.groupValues[3]) ?: continue
            val sku = parseSku(rowDescription)
                ?: parseGenericSku(rowDescription)
                ?: ((index + 1)..minOf(index + 3, lines.lastIndex))
                    .takeWhile { candidateIndex -> !ITEM_NUMERIC_PATTERN.containsMatchIn(lines[candidateIndex]) }
                    .firstNotNullOfOrNull { candidateIndex ->
                        parseSku(lines[candidateIndex]) ?: parseGenericSku(lines[candidateIndex])
                    }
                ?: continue
            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

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

    private fun parseSku(value: String): String? {
        val match = SKU_PATTERN.find(value.uppercase()) ?: return null
        return listOf(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
            match.groupValues[4]
        ).joinToString("-")
    }

    private fun parseGenericSku(value: String): String? = GENERIC_SKU_PATTERN
        .findAll(value.uppercase())
        .map { it.value }
        .firstOrNull { candidate -> candidate != "XX7550" && candidate != "XX7562" }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "SEABOARD FARMS"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*No\.?\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_NUMERIC_PATTERN = Regex(
            """\bXX\d+\s+(.+?)\s+([\d,]+\.\d{4})\s+(?:[A-Z]{1,4}\s+)?([\d,]+\.\d{4,6})\s+[\d,]+\.\d{2}\s+\d{2}/\d{2}/\d{4}\b""",
            RegexOption.IGNORE_CASE
        )
        val SKU_PATTERN = Regex(
            """\b(QAC|CHL)-?(\d+)-(\d+V)-(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val GENERIC_SKU_PATTERN = Regex(
            """\b(?=[A-Z0-9-]{5,}\b)(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\b"""
        )
    }
}
