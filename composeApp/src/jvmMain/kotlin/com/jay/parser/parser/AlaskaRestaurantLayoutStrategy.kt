package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AlaskaRestaurantLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Alaska Restaurant Supply"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("ALASKARESTAURANTSUPPLYINC") &&
                text.contains("POLARFREIGHTFORWARDERS") &&
                text.contains("ACCOUNTINGARSAKCOM") &&
                text.contains("FREETOWA")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("ALASKARESTAURANTSUPPLYINC")) score += 160
        if (text.contains("POLARFREIGHTFORWARDERS")) score += 140
        if (text.contains("ACCOUNTINGARSAKCOM")) score += 120
        if (text.contains("POARSAKCOM")) score += 100
        if (text.contains("FREETOWA")) score += 80
        if (text.contains("ITEMDESCRIPTIONQTYUOFMUNITPRICEEXTENDED")) score += 60
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_NAME)

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = "Alaska Restaurant Supply, Inc.",
            addressLine1 = "c/o Polar Freight Forwarders",
            addressLine2 = "448 E. 18th Street",
            city = "Tacoma",
            state = "WA",
            zip = "98421",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
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

    private companion object {
        const val CUSTOMER_NAME = "ALASKA RESTAURANT SUPPLY"

        val ORDER_NUMBER_PATTERN = Regex("""\b(\d{6}[A-Z]{2,6})\b""", RegexOption.IGNORE_CASE)

        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+(\d+(?:,\d{3})*)\s*[A-Z]{1,5}\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
