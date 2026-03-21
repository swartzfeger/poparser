package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class NationalChemicalsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "National Chemicals"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("NATIONALCHEMICALSINC") &&
                (
                        text.contains("ORDERNO417") ||
                                text.contains("135RICEST") ||
                                text.contains("LEWISTONMN55952") ||
                                text.contains("169500V100")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("NATIONALCHEMICALSINC")) score += 100
        if (text.contains("ORDERNO417")) score += 90
        if (text.contains("135RICEST")) score += 70
        if (text.contains("LEWISTONMN55952")) score += 70
        if (text.contains("IODINETESTPAPERS")) score += 40
        if (text.contains("169500V100")) score += 80

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val customerName = parseCustomerName(clean)
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = mappedCustomer?.terms,
            items = parseItems(clean, mappedCustomer?.priceLevel)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("NATIONALCHEMICALSINC")
        }?.let { "National Chemicals, Inc." }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """Order\s*No\.?\s*\.?\s*(\d{1,10})""",
                RegexOption.IGNORE_CASE
            ).find(normalized)
            if (inline != null) {
                return inline.groupValues[1].trim()
            }
        }

        val joined = lines.joinToString(" ")
        return Regex(
            """Order\s*No\.?\s*\.?\s*(\d{1,10})""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.get(1)?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipIndex = lines.indexOfFirst { it.contains("Ship", ignoreCase = true) }

        val window = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(10)
        } else {
            lines
        }

        for (line in window) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val c = compact(trimmed)

            if (shipToCustomer == null && c.contains("NATIONALCHEMICALSINC")) {
                shipToCustomer = "National Chemicals, Inc."
                continue
            }

            if (addressLine1 == null && c.contains("135RICEST")) {
                addressLine1 = "135 Rice St"
                continue
            }

            val csz = Regex(
                """^(Lewiston),\s*(MN)\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (csz != null) {
                city = "Lewiston"
                state = "MN"
                zip = csz.groupValues[3].trim()
                continue
            }

            if (city == null && state == null && zip == null && c.contains("LEWISTONMN55952")) {
                city = "Lewiston"
                state = "MN"
                zip = if (c.contains("559522108")) "55952-2108" else "55952"
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull {
                compact(it).contains("NATIONALCHEMICALSINC")
            }?.let { "National Chemicals, Inc." }
        }

        if (addressLine1 == null) {
            addressLine1 = lines.firstOrNull {
                compact(it).contains("135RICEST")
            }?.let { "135 Rice St" }
        }

        if (city == null || state == null || zip == null) {
            val cszLine = lines.firstOrNull { compact(it).contains("LEWISTONMN55952") }
            if (cszLine != null) {
                city = "Lewiston"
                state = "MN"
                zip = if (compact(cszLine).contains("559522108")) "55952-2108" else "55952"
            }
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>, priceLevel: String?): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()

            // Example line:
            // RM0054 IodineTestPapers0-50ppm(#500)Item#169- Each 500 2.06 1,030.00
            val mainRow = Regex(
                """^([A-Z0-9-]+)\s+(.+?)Item#([A-Z0-9-]+)\s+[A-Za-z]+\s+(\d+(?:,\d{3})?)\s+[\d,]+\.\d{2}\s+[\d,]+\.\d{2}$""",
                RegexOption.IGNORE_CASE
            ).find(current) ?: continue

            val poDescription = mainRow.groupValues[2].trim()
            val skuHead = mainRow.groupValues[3].trim()
            val quantity = mainRow.groupValues[4].replace(",", "").toDoubleOrNull() ?: continue

            val next = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            // Example wrapped tail:
            // 500V-100
            val skuTailMatch = Regex(
                """^([A-Z0-9-]+)$""",
                RegexOption.IGNORE_CASE
            ).find(next) ?: continue

            val sku = normalizeSku(skuHead + skuTailMatch.groupValues[1].trim())

            val finalDescription = ItemMapper.getItemDescription(sku).ifBlank { poDescription }

            val mappedPrice = ItemMapper.getItemPrice(sku, priceLevel)
            val unitPrice = if (mappedPrice == 0.0) null else mappedPrice

            val key = "$sku|$quantity"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = finalDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
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