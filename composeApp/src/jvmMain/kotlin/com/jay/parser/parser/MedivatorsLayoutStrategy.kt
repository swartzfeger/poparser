package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MedivatorsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "MEDIVATORS"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("MEDIVATORSINC") &&
                text.contains("CANTELACCOUNTSPAYABLESTERISCOM") &&
                text.contains("ITEMMATERIALDESCRIPTIONITEMREVISION") &&
                text.contains("TERMSOFDELIVERYEXWORIGIN") &&
                text.contains("MEDIVATORSINCTAXNO411229121")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("MEDIVATORSINC")) score += 220
        if (text.contains("CANTELACCOUNTSPAYABLESTERISCOM")) score += 200
        if (text.contains("ITEMMATERIALDESCRIPTIONITEMREVISION")) score += 180
        if (text.contains("MEDIVATORSINCTAXNO411229121")) score += 160
        if (text.contains("TERMSOFDELIVERYEXWORIGIN")) score += 140
        if (text.contains("78280000") && text.contains("ACTRILINDICATORTESTSTR")) score += 120
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
        val markerIndex = lines.indexOfFirst { line ->
            line.startsWith("Shipping Address:", ignoreCase = true)
        }
        if (markerIndex < 0 || markerIndex + 2 >= lines.size) return null

        val customer = SHIPPING_CUSTOMER_PATTERN
            .matchEntire(lines[markerIndex])
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: return null
        val cityStateZip = CITY_STATE_ZIP_PATTERN.matchEntire(lines[markerIndex + 2]) ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = lines[markerIndex + 1],
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEach { line ->
            val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@forEach
            val customerMaterial = row.groupValues[1].uppercase()
            val sku = CUSTOMER_MATERIAL_TO_SKU[customerMaterial] ?: return@forEach
            val rawQuantity = parseNumber(row.groupValues[2]) ?: return@forEach
            val unitPrice = parseNumber(row.groupValues[4]) ?: return@forEach

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    // The following "/100 EA" line is the price basis, not a quantity conversion.
                    quantity = rawQuantity,
                    unitPrice = unitPrice,
                    uom = row.groupValues[3].uppercase()
                )
            )
        }
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
        const val CUSTOMER_ID = "MEDIVATORS"

        val CUSTOMER_MATERIAL_TO_SKU = mapOf(
            // ACTRIL indicator: 100 low-level peracetic-acid strips per package.
            "78280-000" to "PAA-50-1V-100"
        )
        val ORDER_NUMBER_PATTERN = Regex(
            """\bPURCHASE\s+ORDER\s+NO\.\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIPPING_CUSTOMER_PATTERN = Regex(
            """^SHIPPING\s+ADDRESS:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([A-Z0-9-]+)\s+\S+\s+\d{2}/\d{2}/\d{4}\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
