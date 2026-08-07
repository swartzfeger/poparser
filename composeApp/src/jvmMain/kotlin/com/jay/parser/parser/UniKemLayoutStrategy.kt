package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class UniKemLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "UNI-KEM.COM"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("UNIKEMCHEMICALSINC") &&
                text.contains("ACCOUNTUNIKEMCOM") &&
                text.contains("WWWUNIKEMCOM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("UNIKEMCHEMICALSINC")) score += 160
        if (text.contains("ACCOUNTUNIKEMCOM")) score += 140
        if (text.contains("WWWUNIKEMCOM")) score += 120
        if (text.contains("802WILLIAMLEIGHDRIVE")) score += 80
        if (text.contains("ITEMNUMBERDESCRIPTIONQTYRATEAMOUNT")) score += 60
        if (text.contains("BHASTYUNIKEMCOM")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "UNI-KEM.COM",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = shipTo.name,
            addressLine1 = shipTo.street,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo {
        val accountIndex = lines.indexOfFirst { compact(it) == "ACCOUNTUNIKEMCOM" }
        if (accountIndex == -1) return ShipTo()

        val values = lines
            .drop(accountIndex + 1)
            .takeWhile { !compact(it).startsWith("VENDORFAX") }
            .filterNot { line ->
                val value = compact(line)
                value.contains("PRECISIONLABORATORIES") ||
                        value.contains("415AIRPARK") ||
                        value.contains("COTTONWOODAZ86326")
            }
        val cityIndex = values.indexOfLast { CITY_STATE_ZIP_PATTERN.matches(it) }
        if (cityIndex <= 0) return ShipTo(name = values.firstOrNull())

        val cityMatch = CITY_STATE_ZIP_PATTERN.matchEntire(values[cityIndex])
        val addressLines = values.subList(1, cityIndex)

        return ShipTo(
            name = values.firstOrNull(),
            street = addressLines.firstOrNull(),
            addressLine2 = addressLines.drop(1).joinToString(", ").ifBlank { null },
            city = cityMatch?.groupValues?.get(1)?.trim(),
            state = cityMatch?.groupValues?.get(2)?.uppercase(),
            zip = cityMatch?.groupValues?.get(3)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.find(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val unitPrice = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val name: String? = null,
        val street: String? = null,
        val addressLine2: String? = null,
        val city: String? = null,
        val state: String? = null,
        val zip: String? = null
    )

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """\b\d{2}/\d{2}/\d{4}\s+([A-Z0-9]+)$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
