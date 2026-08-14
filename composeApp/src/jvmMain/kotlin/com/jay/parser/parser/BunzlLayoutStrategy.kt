package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class BunzlLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "BUNZL"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("BUNZLDISTRIBUTIONUSAINC") &&
                text.contains("CODATASERV") &&
                text.contains("POBOX270417") &&
                text.contains("EFAXCAVCONFIRMATIONSBUNZLUSACOM") &&
                text.contains("MANUFACTUREUMQUANTITYUNITEXTENDED")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("BUNZLDISTRIBUTIONUSAINC")) score += 240
        if (text.contains("CODATASERV")) score += 210
        if (text.contains("POBOX270417")) score += 190
        if (text.contains("EFAXCAVCONFIRMATIONSBUNZLUSACOM")) score += 170
        if (text.contains("MANUFACTUREUMQUANTITYUNITEXTENDED")) score += 150
        if (text.contains("COLOCN")) score += 130
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
        val headerIndex = lines.indexOfFirst { line ->
            line.trimStart().startsWith("SHIP TO:", ignoreCase = true)
        }
        if (headerIndex < 0 || headerIndex + 2 > lines.lastIndex) return null

        val customer = SHIP_TO_CUSTOMER_PATTERN.find(lines[headerIndex])
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: return null
        val addressLine1 = lines[headerIndex + 1].trim()
        val cityStateZip = CITY_STATE_ZIP_PATTERN.matchEntire(lines[headerIndex + 2]) ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
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
        const val CUSTOMER_ID = "BUNZL"

        val ORDER_NUMBER_PATTERN = Regex(
            """PO\s+NUMBER\s+(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """SHIP\s+TO:\s*(.+?)\s+FOB\b""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),?\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+\1\s+.+?\s+PACK\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
