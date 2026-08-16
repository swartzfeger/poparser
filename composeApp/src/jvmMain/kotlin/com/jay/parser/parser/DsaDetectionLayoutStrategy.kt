package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DsaDetectionLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("DSADETECTIONCOM") &&
                text.contains("REMITTOADDRESSSHIPTOADDRESS") &&
                text.contains("ITEMNOITEMNAMEMFRQTYUOM") &&
                text.contains("APDSADETECTIONCOM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("DSADETECTIONCOM")) score += 220
        if (text.contains("PURCHASINGDSADETECTIONCOM")) score += 200
        if (text.contains("APDSADETECTIONCOM")) score += 180
        if (text.contains("120WATERSTSUITE211")) score += 160
        if (text.contains("REMITTOADDRESSSHIPTOADDRESS")) score += 140
        if (text.contains("ITEMNOITEMNAMEMFRQTYUOM")) score += 120
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
            shipToCustomer = "DSA Detection",
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = shipTo?.addressLine2,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val street = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)
        } ?: return null
        val suite = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_SUITE_PATTERN.matchEntire(line)?.groupValues?.get(1)
        }
        val cityStateZip = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_PATTERN.matchEntire(line)
        } ?: return null

        return ShipTo(
            addressLine1 = splitCamelCase(street),
            addressLine2 = suite?.let(::formatSuite),
            city = splitCamelCase(cityStateZip.groupValues[1]),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = row.groupValues[1].uppercase()
        val quantity = parseNumber(row.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(row.groupValues[4]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = row.groupValues[3].uppercase()
        )
    }

    private fun splitCamelCase(value: String): String = value
        .replace(Regex("""(?<=\d)(?=[A-Za-z])"""), " ")
        .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun formatSuite(value: String): String {
        val number = Regex("""\d+""").find(value)?.value
        return number?.let { "Suite $it" } ?: splitCamelCase(value)
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val addressLine1: String,
        val addressLine2: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "DSA DETECTION"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPURCHASE\s*ORDER\s+(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^PRECISION\s*LABORATORIES\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_SUITE_PATTERN = Regex(
            """^415\s*S\.?\s*AIRPARK\s*RD\.?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """^COTTONWOOD,?\s*AZ\s*86326\s+(.+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\S+\s+.+?\s+([A-Z0-9][A-Z0-9-]*)\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+\d{1,2}/\d{1,2}/\d{4}\s+([\d,]+(?:\.\d+)?)\s+[A-Z]{3}\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
