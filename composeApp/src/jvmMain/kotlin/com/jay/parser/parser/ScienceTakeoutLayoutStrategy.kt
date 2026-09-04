package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.abs

class ScienceTakeoutLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SCIENCETAKEOUTCOM") &&
                text.contains("PURCHASEORDERINFORMATION") &&
                text.contains("SHIPPINGADDRESSBILLINGADDRESS") &&
                text.contains("PRECISIONLABORATORIESINC")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SCIENCETAKEOUTCOM")) score += 260
        if (text.contains("SCIENCETAKEOUTSCIENCETAKEOUT")) score += 220
        if (text.contains("PURCHASEORDERINFORMATION")) score += 180
        if (text.contains("SHIPPINGADDRESSBILLINGADDRESS")) score += 160
        if (text.contains("150OFFICEPARKWAY")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::normalizeLine)
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo?.customer,
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = null,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return ORDER_NUMBER_PATTERN.find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val headerIndex = lines.indexOfFirst { line ->
            compact(line).contains("SHIPPINGADDRESSBILLINGADDRESS")
        }
        if (headerIndex < 0) return null

        val itemHeaderIndex = lines.indexOfFirst { line ->
            compact(line).contains("QUANTITYITEMDESCRIPTIONUNITPRICEAMOUNT")
        }.let { index -> if (index >= 0) index else lines.size }

        val addressWindow = lines.subList(headerIndex + 1, itemHeaderIndex)
            .map(::normalizeHumanText)

        val shipToCustomer = addressWindow.firstNotNullOfOrNull { line ->
            SCIENCE_TAKEOUT_NAME_PATTERN.find(line)?.value
        }?.let { "Science Take-Out" }

        val addressLine1 = addressWindow.firstNotNullOfOrNull { line ->
            STREET_ADDRESS_PATTERN.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        }

        val cityStateZip = addressWindow.firstNotNullOfOrNull { line ->
            CITY_STATE_ZIP_PATTERN.find(line)
        } ?: return null

        return ShipTo(
            customer = shipToCustomer ?: "Science Take-Out",
            addressLine1 = addressLine1,
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val quantity = parseNumber(row.groupValues[1]) ?: return@mapNotNull null
        val sku = row.groupValues[2].uppercase()
        val unitPrice = parseNumber(row.groupValues[3]) ?: return@mapNotNull null
        val extension = parseNumber(row.groupValues[4]) ?: return@mapNotNull null
        if (quantity <= 0.0 || unitPrice <= 0.0 || extension <= 0.0) return@mapNotNull null
        if (abs(quantity * unitPrice - extension) > EXTENSION_TOLERANCE) return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = "EA"
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun normalizeLine(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun normalizeHumanText(value: String): String = value
        .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
        .replace(Regex("""([A-Za-z])([0-9])"""), "$1 $2")
        .replace(Regex("""([0-9])([A-Za-z])"""), "$1 $2")
        .replace(Regex(""",(?=\S)"""), ", ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val customer: String,
        val addressLine1: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "SCIENCE TAKEOUT"
        const val EXTENSION_TOLERANCE = 0.02

        val ORDER_NUMBER_PATTERN = Regex(
            """PO\s*Number\s*:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val SCIENCE_TAKEOUT_NAME_PATTERN = Regex(
            """Science\s*Take\s*-?\s*Out""",
            RegexOption.IGNORE_CASE
        )
        val STREET_ADDRESS_PATTERN = Regex(
            """\b(\d{1,6}\s+(?:[A-Za-z0-9.'#-]+\s+){1,8}(?:Road|Rd|Avenue|Ave|Boulevard|Blvd|Drive|Dr|Lane|Ln|Street|St|Way|Court|Ct|Highway|Hwy))\b""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """([A-Za-z][A-Za-z .'-]+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\s*([\d,]+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+\$([\d,]+\.\d{2,3})\s+\$([\d,]+\.\d{2,3})\s*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
