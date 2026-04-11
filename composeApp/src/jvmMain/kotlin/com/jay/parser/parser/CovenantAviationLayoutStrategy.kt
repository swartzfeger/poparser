package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CovenantAviationLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "COVENANT AVIATION SECURITY"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return (
                text.contains("COVENANTAVIATION") ||
                        text.contains("COVENANTAVIATIONSECURITY")
                ) && (
                text.contains("MALLORY") ||
                        text.contains("PO#") ||
                        text.contains("TSAPER100")
                )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("COVENANTAVIATIONSECURITYLLC")) score += 120
        if (text.contains("COVENANTAVIATION")) score += 80
        if (text.contains("MALLORYSAFETY")) score += 80
        if (text.contains("TSAPER100")) score += 80
        if (text.contains("CAS549650WJR")) score += 80
        if (text.contains("FREMONTCA94539")) score += 60
        if (text.contains("NET30")) score += 40

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
            terms = parseTerms(clean) ?: mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        val firstMatches = lines.take(6)

        for (line in firstMatches) {
            val compactLine = compact(line)
            if (compactLine.contains("COVENANTAVIATIONSECURITYLLC")) {
                return "Covenant Aviation Security, LLC"
            }
            if (compactLine.contains("COVENANTAVIATIONSECURITY")) {
                return "Covenant Aviation Security, LLC"
            }
        }

        return lines.firstOrNull {
            compact(it).contains("COVENANTAVIATION")
        }?.trim()
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """PO\s*#?\s*[:\-]?\s*([A-Z0-9\-]+\s*[A-Z0-9\-]*)""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (inline != null) {
                return inline.groupValues[1].trim()
            }
        }

        val joined = lines.joinToString(" ")
        return Regex(
            """PO\s*#?\s*[:\-]?\s*([A-Z0-9\-]+\s*[A-Z0-9\-]*)""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.get(1)?.trim()
    }

    private fun parseTerms(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """TERMS\s*[:\-]?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (match != null) {
                return match.groupValues[1].trim()
            }

            if (compact(normalized).contains("NET30")) {
                return "Net 30"
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipIndex = lines.indexOfFirst {
            val c = compact(it)
            c.contains("SHIPTO") || c.contains("MALLORYSAFETY")
        }

        val searchWindow = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(8)
        } else {
            lines.take(20)
        }

        for (line in searchWindow) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(trimmed)

            if (shipToCustomer == null && compactLine.contains("MALLORYSAFETY")) {
                shipToCustomer = "Mallory Safety & Supply LLC"
                continue
            }

            if (addressLine1 == null && compactLine.contains("44380OSGOODROAD")) {
                addressLine1 = "44380 Osgood Road"
                continue
            }

            if (addressLine2 == null && compactLine.contains("ATTN")) {
                addressLine2 = trimmed
                continue
            }

            val csz = Regex(
                """^(FREMONT),\s*(CA)\s*(94539(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (csz != null) {
                city = "Fremont"
                state = "CA"
                zip = csz.groupValues[3].trim()
                continue
            }

            if (city == null && compactLine.contains("FREMONTCA94539")) {
                city = "Fremont"
                state = "CA"
                zip = "94539"
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

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        val startIndex = lines.indexOfFirst {
            val c = compact(it)
            c.contains("PARTNUMBER") &&
                    c.contains("DESCRIPTION") &&
                    c.contains("PRICE")
        }

        if (startIndex == -1) return emptyList()

        for (i in startIndex + 1 until lines.size) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(line)

            if (line.isBlank()) continue
            if (compactLine.contains("TOTAL")) break
            if (compactLine.contains("PLEASESENDORDERCONFIRMATION")) break

            val match = Regex(
                """^([A-Z0-9\-]+)\s+(.+?)\s+S?\$?\s*([\d,]+\.\d{2})]?\s+\$?\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val sku = match.groupValues[1].trim().uppercase()
            val descriptionRaw = match.groupValues[2].trim()
            val unitPrice = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: continue
            val extPrice = match.groupValues[4].replace(",", "").toDoubleOrNull() ?: continue

            val quantity = if (unitPrice != 0.0) {
                extPrice / unitPrice
            } else {
                continue
            }

            val normalizedQuantity = if (quantity % 1.0 == 0.0) {
                quantity.toInt().toDouble()
            } else {
                quantity
            }

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                descriptionRaw.ifBlank { sku }
            }

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = normalizedQuantity,
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