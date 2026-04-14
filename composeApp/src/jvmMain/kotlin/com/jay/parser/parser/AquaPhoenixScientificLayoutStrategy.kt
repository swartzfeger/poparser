package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AquaPhoenixScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "AQUA PHOENIX SCIENTIFIC"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("AQUAPHOENIX")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("AQUAPHOENIX")) score += 100
        if (text.contains("PO26030078")) score += 100
        if (text.contains("860GITTSRUNROAD")) score += 80
        if (text.contains("HANOVERPA17331")) score += 80
        if (text.contains("ACHIN30DAYS")) score += 70
        if (text.contains("PHO1V50")) score += 70
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val joined = clean.joinToString(" ")
        val shipTo = parseShipTo(clean)
        val customerName = "AquaPhoenix Scientific"
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(joined),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(joined) ?: mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(joined: String): String? {
        return Regex("""\bPO\d{6,12}\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.trim()
            ?.uppercase()
    }

    private fun parseTerms(joined: String): String? {
        return Regex(
            """Payment\s+Terms:\s*([A-Za-z0-9 ]+)""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.get(1)?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        for (raw in lines) {
            val line = normalize(raw)

            if (shipToCustomer == null) {
                val shipMatch = Regex(
                    """Vendor\s+No\.\s+\S+\s+(.+)$""",
                    RegexOption.IGNORE_CASE
                ).find(line)

                if (shipMatch != null) {
                    shipToCustomer = shipMatch.groupValues[1].trim()
                }
            }

            if (addressLine1 == null && line.contains("860 Gitts Run Road", ignoreCase = true)) {
                addressLine1 = "860 Gitts Run Road"
            }

            if (city == null || state == null || zip == null) {
                val cszMatch = Regex(
                    """(Hanover)\s*,\s*(PA)\s*(17331(?:-\d{4})?)""",
                    RegexOption.IGNORE_CASE
                ).find(line)

                if (cszMatch != null) {
                    city = "Hanover"
                    state = "PA"
                    zip = cszMatch.groupValues[3].trim()
                }
            }
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer ?: "AquaPhoenix Scientific PA",
            addressLine1 = addressLine1,
            addressLine2 = null,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        for (raw in lines) {
            val line = normalize(raw)
            if (line.isBlank()) continue

            val match = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+(\d+(?:,\d{3})?)\s+EA\s+\S+\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val sku = match.groupValues[1].trim().uppercase()
            val descriptionRaw = match.groupValues[2].trim()
            val quantity = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: continue
            val extAmount = match.groupValues[4].replace(",", "").toDoubleOrNull() ?: continue
            val unitPrice = if (quantity != 0.0) extAmount / quantity else continue

            items.add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        descriptionRaw.ifBlank { sku }
                    },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun normalize(value: String): String {
        return value
            .replace("[=]", " ")
            .replace("—", " ")
            .replace("“", " ")
            .replace("”", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun compact(value: String): String {
        return normalize(value)
            .uppercase()
            .replace(Regex("""[^A-Z0-9#]"""), "")
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}