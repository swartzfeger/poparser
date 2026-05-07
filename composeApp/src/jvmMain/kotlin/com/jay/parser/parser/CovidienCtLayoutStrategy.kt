package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CovidienCtLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "COVIDIEN CT"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("COVIDIENLP") ||
                text.contains("MEDTRONIC") ||
                text.contains("SURGICALSOLUTIONS") ||
                text.contains("VNDITEM:290-1-1515")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("COVIDIENLP")) score += 140
        if (text.contains("SURGICALSOLUTIONS")) score += 120
        if (text.contains("MEDTRONIC")) score += 100
        if (text.contains("PURCHASEORDERNUMBER:")) score += 80
        if (text.contains("VNDITEM:290-1-1515")) score += 80
        if (text.contains("NORTHHAVEN")) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map {
            it.replace(Regex("""\s+"""), " ").trim()
        }

        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "COVIDIEN CT",
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
        return Regex("""Purchase\s+Order\s+Number:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("NET 90 DAYS", ignoreCase = true) -> "Net 90 Days"
            joined.contains("NET90 DAYS", ignoreCase = true) -> "Net 90 Days"
            joined.contains("NET90DAYS", ignoreCase = true) -> "Net 90 Days"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        return ShipToBlock(
            shipToCustomer = "SURGICAL SOLUTIONS",
            addressLine1 = "195 McDermott Road",
            addressLine2 = "a GBU of COVIDIEN LP",
            city = "North Haven",
            state = "CT",
            zip = "06473"
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            val rowMatch = Regex(
                """^(\d+)\s+([A-Z0-9-]+)\s+RAW\s+EA\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (rowMatch != null) {
                val quantity = rowMatch.groupValues[3].toDoubleOrNull()
                val unitPrice = rowMatch.groupValues[4].toDoubleOrNull()

                var description: String? = null
                var sku: String? = null

                var j = i + 1
                while (j < lines.size && j <= i + 8) {
                    val next = lines[j].trim()

                    if (description == null &&
                        next.contains("INDICATOR", ignoreCase = true)
                    ) {
                        description = next
                            .replace(Regex("""\s+\d{4}-\d{2}-\d{2}.*$"""), "")
                            .trim()
                    }

                    val vndMatch = Regex("""Vnd\s+Item:\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
                        .find(next)

                    if (vndMatch != null) {
                        sku = vndMatch.groupValues[1].trim().uppercase()
                        break
                    }

                    j++
                }

                if (!sku.isNullOrBlank() && quantity != null && unitPrice != null) {
                    items.add(
                        item(
                            sku = sku,
                            description = ItemMapper.getItemDescription(sku).ifBlank {
                                description ?: sku
                            },
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )
                }

                i = j
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
}