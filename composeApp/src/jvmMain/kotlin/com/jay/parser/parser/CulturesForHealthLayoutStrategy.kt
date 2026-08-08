package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CulturesForHealthLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Cultures for Health"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CULTURESFORHEALTH") &&
                text.contains("PRECISIONLABORATORIES") &&
                text.contains("OURSKU4030") &&
                text.contains("PH28441V25")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CULTURESFORHEALTH")) score += 160
        if (text.contains("OURSKU4030")) score += 140
        if (text.contains("PHINDICATORSTRIPS25COUNT")) score += 120
        if (text.contains("1422MOGADORERD")) score += 100
        if (text.contains("115WBARTGESST")) score += 100
        if (text.contains("365HOLDINGSCOMPOTERMS")) score += 80
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
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo {
        val text = compact(lines.joinToString("\n"))
        return when {
            text.contains("1422MOGADORERD") && text.contains("KENTOH44240") -> ShipTo(
                customer = "CULTURES FOR HEALTH LLC",
                addressLine1 = "1422 Mogadore Rd",
                city = "Kent",
                state = "OH",
                zip = "44240"
            )

            text.contains("115WBARTGESST") && text.contains("AKRONOH44311") -> ShipTo(
                customer = "CULTURES FOR HEALTH",
                addressLine1 = "115 W Bartges St",
                city = "Akron",
                state = "OH",
                zip = "44311"
            )

            else -> ShipTo(customer = CUSTOMER_ID)
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
        val customer: String?,
        val addressLine1: String? = null,
        val city: String? = null,
        val state: String? = null,
        val zip: String? = null
    )

    private companion object {
        const val CUSTOMER_ID = "CULTURES FOR HEALTH"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPurchase\s*#\s*(PO\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+\[\d+]\s*.+?\(([A-Z0-9]+(?:-[A-Z0-9]+)+)\)\s+([\d,]+(?:\.\d+)?)\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
