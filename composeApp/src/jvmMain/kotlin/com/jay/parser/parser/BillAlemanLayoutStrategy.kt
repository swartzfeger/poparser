package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class BillAlemanLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Bill Aleman Inc"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("BILLALEMANINC") &&
                text.contains("TEL3055954454") &&
                text.contains("FAX3055954774") &&
                text.contains("ITEMQTYPARTNODESCRIPTIONRATEAMOUNT") &&
                text.contains("ACCT5WX417")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("BILLALEMANINC")) score += 220
        if (text.contains("TEL3055954454")) score += 190
        if (text.contains("FAX3055954774")) score += 170
        if (text.contains("ITEMQTYPARTNODESCRIPTIONRATEAMOUNT")) score += 150
        if (text.contains("ACCT5WX417")) score += 130
        if (text.contains("PURCHASEORDER")) score += 110
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
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
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
        val headerCustomer = lines.firstOrNull { line -> compact(line) == "BILLALEMANINC" }
        val rawCustomer = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CUSTOMER_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val customer = if (headerCustomer != null && compact(rawCustomer) == compact(headerCustomer)) {
            headerCustomer
        } else {
            rawCustomer
        }
        val rawStreet = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZipText = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_LINE_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZip = CITY_STATE_ZIP_PATTERN.matchEntire(cityStateZipText) ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = rawStreet.replace(Regex("""^(\d+)([A-Z])"""), "$1 $2"),
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val quantity = parseNumber(match.groupValues[1]) ?: return@mapNotNull null
        val sku = match.groupValues[2].uppercase()
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
        const val CUSTOMER_ID = "BILL ALEMAN INC"

        val ORDER_NUMBER_PATTERN = Regex("""\bPO\d+\b""", RegexOption.IGNORE_CASE)
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """^PRECISION\s*LABS\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^415\s*AIRPARK\s*(?:RD|ROAD)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_LINE_PATTERN = Regex(
            """^COTTONWOOD,?\s*AZ\s*86326(?:-4050)?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),?\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([\d,]+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
