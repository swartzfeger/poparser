package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class EnvironicsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Environics Inc"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("ENVIRONICSINC") &&
                text.contains("69INDUSTRIALPARKROADEAST") &&
                text.contains("PHONE8608721111") &&
                text.contains("ENVIRONICSACHTOBEGIN") &&
                text.contains("OURITEMNUMBERDESCRIPTIONYOURITEMNUMBER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("ENVIRONICSINC")) score += 220
        if (text.contains("69INDUSTRIALPARKROADEAST")) score += 190
        if (text.contains("PHONE8608721111")) score += 170
        if (text.contains("ENVIRONICSACHTOBEGIN")) score += 150
        if (text.contains("OURITEMNUMBERDESCRIPTIONYOURITEMNUMBER")) score += 130
        if (text.contains("COMPONENTSSUPPLIEDUNDERTHISPURCHASEORDERMUSTBEROHS3COMPLIANT")) score += 110
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
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo?.customer,
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = shipTo?.addressLine2,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val headerIndex = lines.indexOfFirst { line -> compact(line) == "PONUMBERPAGE" }
        if (headerIndex >= 0) {
            ORDER_NUMBER_PATTERN.find(lines.getOrNull(headerIndex + 1).orEmpty())
                ?.groupValues
                ?.get(1)
                ?.let { return it }
        }

        return lines.firstNotNullOfOrNull { line ->
            ORDER_NUMBER_PATTERN.matchEntire(line)?.groupValues?.get(1)
        }
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val customer = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CUSTOMER_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val addressLine1 = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZipText = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: return null
        val cityStateZip = CITY_STATE_ZIP_PATTERN.matchEntire(cityStateZipText) ?: return null
        val addressLine2 = lines.firstOrNull { line ->
            line.startsWith("ATTN:", ignoreCase = true)
        }?.trim()?.trimEnd(',')

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = cityStateZip.groupValues[1].trim(),
            state = cityStateZip.groupValues[2].uppercase(),
            zip = cityStateZip.groupValues[3]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (index in lines.indices) {
            val row = ITEM_ROW_PATTERN.matchEntire(lines[index]) ?: continue
            val quantity = parseNumber(row.groupValues[1]) ?: continue
            val uom = row.groupValues[2].uppercase()
            val sku = row.groupValues[3].uppercase()
            val unitPrice = (index + 1..minOf(index + 3, lines.lastIndex))
                .firstNotNullOfOrNull { nextIndex ->
                    UNIT_PRICE_PATTERN.find(lines[nextIndex])
                        ?.groupValues
                        ?.get(1)
                        ?.let(::parseNumber)
                } ?: continue

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice,
                    uom = uom
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
        val addressLine2: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "ENVIRONICS INC"

        val ORDER_NUMBER_PATTERN = Regex("""^(\d+-\d+)\s+\d+$""")
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """^PRECISION\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^PRECISION\s+LABORATORIES\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """^415\s+AIRPARK\s+(?:RD|ROAD)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+\S+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+\d{1,2}/\d{1,2}/\d{2,4}$""",
            RegexOption.IGNORE_CASE
        )
        val UNIT_PRICE_PATTERN = Regex(
            """([\d,]+\.\d{1,4})(?:\s+CHANGE)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
