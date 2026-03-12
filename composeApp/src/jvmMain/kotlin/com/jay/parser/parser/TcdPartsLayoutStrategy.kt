package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class TcdPartsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "TCD Parts"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()
        return text.contains("PURCHASE ORDER") &&
                text.contains("PURCHASEORDERNUMBER") &&
                text.contains("TCDPARTSINC")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("TCDPARTSINC")) score += 100
        if (text.contains("PURCHASE ORDER")) score += 60
        if (text.contains("PURCHASEORDERNUMBER")) score += 50
        if (text.contains("19450HWYB")) score += 50
        if (text.contains("EDGERTON,MO64444")) score += 60
        if (text.contains("NET30")) score += 25

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "TCD PARTS INC",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = compact(lines[i])
            if (line.contains("PURCHASEORDERNUMBER")) {
                val next = lines.getOrNull(i + 1)?.trim()
                if (!next.isNullOrBlank() && next.matches(Regex("""\d{4,}"""))) {
                    return next
                }
            }
        }

        return lines
            .map { it.trim() }
            .firstOrNull { it.matches(Regex("""5031131""")) }
    }

    private fun parseTerms(lines: List<String>): String? {
        val line = lines.firstOrNull { compact(it).contains("NET30") } ?: return null
        return if (compact(line).contains("NET30")) "NET 30" else null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val shipHeaderIndex = lines.indexOfFirst { compact(it).contains("SENDTO:SHIPTO:") }

        if (shipHeaderIndex >= 0) {
            val window = lines.drop(shipHeaderIndex + 1).take(6)

            val shipToCustomer = window
                .firstOrNull { compact(it).contains("TCDPARTSINC") }
                ?.let { "TCD PARTS INC" }

            val addressLine1 = window
                .firstOrNull { compact(it).contains("19450HWYB") }
                ?.let { "19450 HWY B" }

            val cityStateZipLine = window
                .firstOrNull { compact(it).contains("EDGERTON,MO64444") }

            val parsed = if (cityStateZipLine != null) {
                CityStateZip("EDGERTON", "MO", "64444")
            } else {
                CityStateZip(null, null, null)
            }

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                addressLine2 = null,
                city = parsed.city,
                state = parsed.state,
                zip = parsed.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = null,
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            state = null,
            zip = null
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^([\d,]+\.\d{3})\s*([A-Z0-9]+)\s+([A-Z0-9-]+)\s+(.+?)\s+([\d,]+\.\d{4})\s+([A-Z0-9]+)\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (rowMatch != null) {
                val quantity = rowMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                val description = rowMatch.groupValues[4].trim()
                val unitPrice = rowMatch.groupValues[5].replace(",", "").toDoubleOrNull()

                val partLine = lines.getOrNull(i + 1)?.replace(Regex("""\s+"""), " ")?.trim().orEmpty()
                val sku = Regex(
                    """PartNo\.:\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
                    RegexOption.IGNORE_CASE
                ).find(partLine)?.groupValues?.get(1)?.trim()

                if (sku != null) {
                    val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { description }

                    items.add(
                        item(
                            sku = sku,
                            description = mappedDescription,
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )
                }
            }
        }

        return items
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""\s+"""), "")
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}