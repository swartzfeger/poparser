package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class KochFoodsMorristownLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Koch Foods - Morristown"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("KOCHFOODSMORRISTOWNKILL") &&
                text.contains("SHIPTOACCOUNT60840000") &&
                text.contains("PRECISIONLABORATORIES")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("KOCHFOODSMORRISTOWNKILL")) score += 160
        if (text.contains("SHIPTOACCOUNT60840000")) score += 140
        if (text.contains("123SOUTHFAIRMOUNTAVENUE")) score += 120
        if (text.contains("ORDERSPRECLABORATORIESCOM")) score += 100
        if (text.contains("PARTCODEVENDORPARTNOORDERQTY")) score += 80
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = SHIP_TO_CUSTOMER,
            addressLine1 = shipTo.addressLine1,
            city = shipTo.city,
            state = stateForZip(shipTo.zip),
            zip = shipTo.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo {
        val shipStart = lines.indexOfFirst {
            compact(it).contains(compact(SHIP_TO_CUSTOMER))
        }.coerceAtLeast(0)
        val billToIndex = lines.withIndex().firstOrNull { (index, line) ->
            index > shipStart && compact(line).contains("BILLTO")
        }?.index ?: lines.size
        val shipToBlock = lines.subList(shipStart, billToIndex)

        val addressLine1 = shipToBlock
            .firstOrNull { compact(it).contains("123SOUTHFAIRMOUNTAVENUE") }
            ?.let { SHIP_TO_STREET }
        val zip = shipToBlock.firstNotNullOfOrNull { line ->
            ZIP_PATTERN.find(line)?.groupValues?.get(1)
        }

        return ShipTo(
            addressLine1 = addressLine1,
            city = shipToBlock.firstOrNull { it.equals("Morristown", ignoreCase = true) }
                ?.replaceFirstChar { it.uppercase() }
                ?: "Morristown",
            zip = zip
        )
    }

    private fun stateForZip(zip: String?): String? {
        val numericZip = zip?.take(5)?.toIntOrNull() ?: return null
        return when (numericZip) {
            in 37000..38599 -> "TN"
            else -> null
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[3]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val addressLine1: String?,
        val city: String?,
        val zip: String?
    )

    private companion object {
        const val CUSTOMER_ID = "KOCH FOODS-MORRISTOW"
        const val SHIP_TO_CUSTOMER = "Koch Foods - Morristown Kill"
        const val SHIP_TO_STREET = "123 South Fairmount Avenue"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*No\s*:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ZIP_PATTERN = Regex("""\b(\d{5})(?:-\d{4})?\b""")
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+[A-Z]+\s+(\$?[\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
