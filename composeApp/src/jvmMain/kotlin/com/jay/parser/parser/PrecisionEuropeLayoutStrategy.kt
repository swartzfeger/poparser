package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class PrecisionEuropeLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "PRECISION EUROPE"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("PRECISIONEUROPE") &&
                text.contains("PURCHASEORDER") &&
                text.contains("PO#")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("PRECISIONEUROPE")) score += 100
        if (text.contains("PURCHASEORDER")) score += 80
        if (text.contains("HISPEKHOUSE")) score += 60
        if (text.contains("BOUGHTONFAIRLANEPITSFORDROAD")) score += 60
        if (text.contains("MOULTONNORTHAMPTONNN37RS")) score += 60
        if (text.contains("QUANTITYITEM#DESCRIPTIONLOT#UNITPRICEEXTENSION")) score += 80

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)
        val customerName = parseCustomerName(clean)
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

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
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull { compact(it).contains("PRECISIONEUROPE") }
            ?.let { "Precision Europe" }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """PO\s*#\s*:\s*([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (inline != null) {
                return inline.groupValues[1].trim()
            }
        }

        val joined = lines.joinToString(" ")
        return Regex(
            """PO\s*#\s*:\s*([A-Z0-9-]+)""",
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

        val shipIndex = lines.indexOfFirst { compact(it).contains("SHIPTO") }

        val searchWindow = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(8)
        } else {
            lines.take(12)
        }

        for (line in searchWindow) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(trimmed)

            if (shipToCustomer == null && compactLine == "PRECISIONEUROPEPRECISIONEUROPE") {
                shipToCustomer = "Precision Europe"
                continue
            }

            if (shipToCustomer == null && compactLine == "PRECISIONEUROPE") {
                shipToCustomer = "Precision Europe"
                continue
            }

            if (addressLine1 == null && compactLine.contains("HISPEKHOUSE")) {
                addressLine1 = "Hi Spek House"
                continue
            }

            if (addressLine2 == null && compactLine.contains("BOUGHTONFAIRLANEPITSFORDROAD")) {
                addressLine2 = "Boughton Fair Lane, Pitsford Road"
                continue
            }

            if (compactLine.contains("MOULTONNORTHAMPTONNN37RS")) {
                city = "Moulton"
                state = "UK"
                zip = "NN3 7RS"
            }
        }

        if (shipToCustomer == null) shipToCustomer = "Precision Europe"

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        val startIndex = lines.indexOfFirst {
            val compactLine = compact(it)
            compactLine.contains("QUANTITY") &&
                    compactLine.contains("ITEM#") &&
                    compactLine.contains("DESCRIPTION") &&
                    compactLine.contains("UNITPRICE")
        }

        if (startIndex == -1) return emptyList()

        for (i in startIndex + 1 until lines.size) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            if (line.isBlank()) continue
            if (line.contains("Subtotal", ignoreCase = true)) break

            val match = Regex(
                """^(\d+(?:\.\d+)?)\s+([A-Z0-9-]+)\s+(.*?)(?:\s+(\d+))?\s+\$\s*([\d,]+\.\d{2})\s+\$\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val quantity = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val sku = match.groupValues[2].trim().uppercase()
            val descriptionRaw = match.groupValues[3].trim()
            val unitPrice = match.groupValues[5].replace(",", "").toDoubleOrNull() ?: continue

            items.add(
                item(
                    sku = sku,
                    description = descriptionRaw.ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9#]"""), "")
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