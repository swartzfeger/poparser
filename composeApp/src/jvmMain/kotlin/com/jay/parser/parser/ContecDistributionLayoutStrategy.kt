package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ContecDistributionLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CONTECPRODUCTIONDATABASEORDER") &&
                text.contains("CONTECINCFLATWOODDISTRIBUTIONCENTER") &&
                text.contains("CONTECLOGISTICSAT18664364804") &&
                text.contains("LINEYOURPARTNOYOURDESCRIPTIONDOCKQTYDUEQUANTITYUOMPRICE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CONTECPRODUCTIONDATABASEORDER")) score += 240
        if (text.contains("CONTECINCFLATWOODDISTRIBUTIONCENTER")) score += 220
        if (text.contains("767FLATWOODINDUSTRIALDRIVE")) score += 200
        if (text.contains("CONTECLOGISTICSAT18664364804")) score += 180
        if (text.contains("LUNDERWOODCONTECINCCOM")) score += 160
        if (text.contains("LINEYOURPARTNOYOURDESCRIPTIONDOCKQTYDUEQUANTITYUOMPRICE")) score += 140
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
                ORDER_NUMBER_PATTERN.matchEntire(line)?.groupValues?.get(1)
            },
            shipToCustomer = "CONTEC, INC. FLATWOOD DISTRIBUTION CENTER",
            addressLine1 = shipTo?.addressLine1,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val street = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZip = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_PATTERN.matchEntire(line)
        } ?: return null

        return ShipTo(
            addressLine1 = street,
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = row.groupValues[1].uppercase()
        val quantity = parseNumber(row.groupValues[4]) ?: return@mapNotNull null
        val unitPrice = parseNumber(row.groupValues[6]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = row.groupValues[5].uppercase()
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val addressLine1: String,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "CONTEC DISTRIBUTION"

        val ORDER_NUMBER_PATTERN = Regex(
            """^\d+\s+\d+\s+\d{1,2}-[A-Z]{3}-\d{4}\s+(\d+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^415\s+AIRPARK\s+DRIVE\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """^COTTONWOOD\s+AZ\s+86326\s+-?\s*(.+?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)\s+-?$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+([\d,]+(?:\.\d+)?)\s+\d{1,2}-[A-Z]{3}-\d{4}$""",
            RegexOption.IGNORE_CASE
        )
    }
}
