package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AlphaChemicalServicesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "ALPHA CHEMICAL SERVI"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("ALPHACHEMICALSERVICESINC") &&
                text.contains("ALPHACHEMICALWAREHOUSE") &&
                text.contains("PHONE8004649872") &&
                text.contains("PRECISIONLABORATORIES") &&
                text.contains("POTERMSCONDITIONS")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("ALPHACHEMICALSERVICESINC")) score += 220
        if (text.contains("ALPHACHEMICALWAREHOUSE")) score += 200
        if (text.contains("PHONE8004649872")) score += 180
        if (text.contains("POTERMSCONDITIONS")) score += 150
        if (text.contains("RS99126") || text.contains("RS99125CS")) score += 130
        if (text.contains("46MORTONSTREET")) score += 110
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
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val labelIndex = lines.indexOfFirst { line ->
            compact(line).contains("PONUMBER")
        }
        if (labelIndex < 0) return null

        return lines
            .drop(labelIndex)
            .take(3)
            .firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            }
    }

    private fun parseShipTo(lines: List<String>): ShipTo {
        val customer = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CUSTOMER_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        } ?: "ALPHA CHEMICAL WAREHOUSE"
        val addressLine1 = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_STREET_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        }
        val addressLine2 = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_ATTENTION_PATTERN.matchEntire(line)?.groupValues?.get(1)?.trim()
        }?.replace(Regex("""^ATTETNION\b""", RegexOption.IGNORE_CASE), "ATTENTION")
        val cityStateZip = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_CITY_PATTERN.matchEntire(line)
        }

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = cityStateZip?.groupValues?.get(1)?.trim() ?: "STOUGHTON",
            state = cityStateZip?.groupValues?.get(2)?.uppercase() ?: "MA",
            zip = cityStateZip?.groupValues?.get(3) ?: "02072"
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { index, line ->
            val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@forEachIndexed
            val context = lines.subList(index, minOf(index + 4, lines.size)).joinToString(" ")
            val sku = when {
                context.contains("RS99126", ignoreCase = true) -> "169-144V-100"
                context.contains("RS99125CS", ignoreCase = true) -> "106-144V-100"
                else -> return@forEachIndexed
            }
            val totalQuantity = parseNumber(row.groupValues[4]) ?: return@forEachIndexed
            val unitPrice = parseNumber(row.groupValues[5]) ?: return@forEachIndexed

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        row.groupValues[1].trim()
                    },
                    quantity = totalQuantity,
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
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "ALPHA CHEMICAL SERVI"

        val ORDER_NUMBER_PATTERN = Regex("""\b(\d{5})\b\s*$""")
        val SHIP_TO_CUSTOMER_PATTERN = Regex(
            """^PRECISION\s+LABORATORIES\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_STREET_PATTERN = Regex(
            """^415\s+AIRPARK\s+DRIVE\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_ATTENTION_PATTERN = Regex(
            """^COTTONWOOD,?\s+AZ\s+86326\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """^USA\s+(.+?),\s*([A-Z]{2})\s+(\d{5})$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(.+?)\s+(\d+)\s+1\s+E\s+(ITEM|CASE)\s+(\d+)\s+E\s+USD\s+([\d,]+(?:\.\d+)?)/E\s+USD\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
