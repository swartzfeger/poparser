package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class QualityScienceLabsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "QUALITY SCIENCE LABS"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("QUALITYSCIENCELABS,LLC") ||
                (text.contains("PURCHASEORDERNUMBER:") && text.contains("SUPPLIER SHIPTO")) ||
                text.contains("LAKEGEORGE,CO80827")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("QUALITYSCIENCELABS,LLC")) score += 120
        if (text.contains("PURCHASEORDERNUMBER:")) score += 90
        if (text.contains("SUPPLIER SHIPTO")) score += 80
        if (text.contains("USPS:POBOX159")) score += 70
        if (text.contains("LAKEGEORGE,CO80827")) score += 70
        if (text.contains("DANIELLEGURIEN")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "QUALITY SCIENCE LABS, LLC",
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
        val joined = lines.joinToString(" ")
        return Regex("""PURCHASEORDERNUMBER:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("Net30", ignoreCase = true) -> "Net 30"
            joined.contains("Net 30", ignoreCase = true) -> "Net 30"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        // Current layout consistently places the ship-to block in the opening lines
        // and again in the right-hand side of the combined SUPPLIER / SHIP TO block.
        // We prefer the explicit opening block because it preserves address line 2 cleanly.
        val company = lines.firstOrNull { normalize(it) == "QUALITYSCIENCELABS,LLC" }?.let { "Quality Science Labs, LLC" }
        val address1 = lines.firstOrNull { normalize(it) == "37888USHIGHWAY24" }?.let { "37888 US Highway 24" }

        val poBoxLine = lines.firstOrNull {
            normalize(it) == "POBOX159" || normalize(it) == "USPS:POBOX159"
        }

        val address2 = when (normalize(poBoxLine.orEmpty())) {
            "POBOX159" -> "USPS: PO Box 159"
            "USPS:POBOX159" -> "USPS: PO Box 159"
            else -> null
        }

        val cityStateZipLine = lines.firstOrNull { normalize(it) == "LAKEGEORGE,CO80827" }
        val csz = parseCityStateZip(cityStateZipLine)

        return ShipToBlock(
            shipToCustomer = company,
            addressLine1 = address1,
            addressLine2 = address2,
            city = csz?.city,
            state = csz?.state,
            zip = csz?.zip
        )
    }

    private fun parseCityStateZip(line: String?): CityStateZip? {
        if (line.isNullOrBlank()) return null

        val normalized = line
            .replace("LakeGeorge", "Lake George")
            .replace("CO80827", "CO 80827")
            .trim()

        val match = Regex("""^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            val match = Regex(
                """^(\d+)\s+([A-Z0-9-]+)\s+(.+?)\s+([\d.]+)\s+\$([\d.]+)\s+\$([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (match != null) {
                val sku = match.groupValues[2].trim().uppercase()
                val descFromLine = match.groupValues[3].trim()
                val quantity = match.groupValues[4].toDoubleOrNull()
                val unitPrice = match.groupValues[5].toDoubleOrNull()

                if (quantity != null && unitPrice != null) {
                    val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { "" }
                    val description = if (mappedDescription.isNotBlank()) {
                        mappedDescription
                    } else {
                        descFromLine
                    }

                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = description,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }
            }

            i++
        }

        return items
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
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
        val city: String,
        val state: String,
        val zip: String
    )
}