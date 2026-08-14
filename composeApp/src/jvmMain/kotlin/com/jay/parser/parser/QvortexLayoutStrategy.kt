package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class QvortexLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "QVORTEX LLC"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("QVORTEXLLC") &&
                text.contains("OFFICEQVORTEXCHEMICALSCOM") &&
                text.contains("UNITCOSTORDEREDUOMTOTALCOST") &&
                text.contains("PREPAIDBILLED")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("QVORTEXLLC")) score += 220
        if (text.contains("OFFICEQVORTEXCHEMICALSCOM")) score += 190
        if (text.contains("UNITCOSTORDEREDUOMTOTALCOST")) score += 170
        if (text.contains("PREPAIDBILLED")) score += 150
        if (text.contains("PURCHASEORDER")) score += 130
        if (text.contains("CONTACTPRECISIONLABS")) score += 110
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
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()?.replaceFirst("PLM0", "PLMO")
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
        val cityStateZipText = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_LINE_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZip = CITY_STATE_ZIP_PATTERN.matchEntire(cityStateZipText) ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { index, line ->
            val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@forEachIndexed
            var sku = match.groupValues[1].uppercase()
            if (sku.endsWith("-") && index < lines.lastIndex) {
                val finalSegment = SKU_CONTINUATION_PATTERN.find(lines[index + 1])?.groupValues?.get(1)
                if (finalSegment != null) sku += finalSegment
            }

            val quantity = parseNumber(match.groupValues[3]) ?: return@forEachIndexed
            val unitPrice = parseNumber(match.groupValues[2]) ?: return@forEachIndexed

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice
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
        const val CUSTOMER_ID = "QVORTEX LLC"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bP(?:LM[O0]|QV)[A-Z0-9]+\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """^PRECISION\s+LABS?\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^415\s+(?:S\.?\s+)?AIRPARK\s+(?:RD|ROAD)\s+(.+)$""",
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
            """^\d+\s+PURCHASE\s+([A-Z0-9]+(?:-[A-Z0-9]+)+-?)\s+.+?\s+\$\s*([\d,]+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\.?\s+EA\s+\$\s*[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val SKU_CONTINUATION_PATTERN = Regex("""^(\d+)\b""")
    }
}
