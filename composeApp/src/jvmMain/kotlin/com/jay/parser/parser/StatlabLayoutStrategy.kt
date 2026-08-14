package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class StatlabLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "STATLAB"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("STATLABMEDICALPRODUCTS") &&
                text.contains("2090COMMERCEDRIVE") &&
                text.contains("STATLABSUPPORTSUNSETTRANSCOM") &&
                text.contains("LINEPARTNUMBERREVDESCRIPTIONORDERQTYUNITPRICEEXTPRICE") &&
                text.contains("ORDERCONFIRMATIONREQUIRED")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("STATLABMEDICALPRODUCTS")) score += 220
        if (text.contains("2090COMMERCEDRIVE")) score += 200
        if (text.contains("STATLABSUPPORTSUNSETTRANSCOM")) score += 180
        if (text.contains("LINEPARTNUMBERREVDESCRIPTIONORDERQTYUNITPRICEEXTPRICE")) score += 160
        if (text.contains("ORDERCONFIRMATIONREQUIRED")) score += 140
        if (text.contains("FEDEXACCOUNT158093731")) score += 120
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
            shipToCustomer = shipTo?.customer,
            addressLine1 = shipTo?.addressLine1,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val customer = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CUSTOMER_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val addressLine1 = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZip = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_PATTERN.matchEntire(line)
        } ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            city = cityStateZip.groupValues[1].trim(),
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

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val customer: String,
        val addressLine1: String,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "STATLAB"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s+NUMBER:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """^PRECISION\s+LABORATORIES\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^COTTONWOOD\s+AZ\s+86326(?:-4050)?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """^USA\s+(.+?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+(\$[\d,]+(?:\.\d+)?)/\d+\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
