package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class NalcoCompanyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Nalco Company"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("NALCOCOMPANY") &&
                (
                        text.contains("PONUMBER4505142690") ||
                                text.contains("NALCOCOMPANYLLC") ||
                                text.contains("NALCOGLOBALEQTSOLN") ||
                                text.contains("CHL30025V100")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("NALCOCOMPANYLLC")) score += 100
        if (text.contains("PONUMBER4505142690")) score += 90
        if (text.contains("NALCOGLOBALEQTSOLN")) score += 80
        if (text.contains("6233WEST65THSTREET")) score += 70
        if (text.contains("CHICAGOIL60638")) score += 70
        if (text.contains("SUPPLIERMATLCHL30025V100")) score += 80
        if (text.contains("PRECISIONCHLORINESTRIPS")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val customerName = parseCustomerName(clean)
        val shipTo = parseShipTo(clean)
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
            items = parseItems(clean, mappedCustomer?.priceLevel)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("NALCOCOMPANYLLC")
        }?.let { "Nalco Company LLC" }
            ?: lines.firstOrNull {
                compact(it) == "NALCOCOMPANY"
            }?.let { "Nalco Company" }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """PO\s*Number\s*[:#]?\s*(\d{8,12})""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (inline != null) {
                return inline.groupValues[1].trim()
            }
        }

        val joined = lines.joinToString(" ")
        return Regex(
            """PO\s*Number\s*[:#]?\s*(\d{8,12})""",
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

        val shipIndex = lines.indexOfFirst { compact(it).contains("SHIPTOADDRESS") }

        val window = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(8)
        } else {
            lines
        }

        for (line in window) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val c = compact(trimmed)

            if (shipToCustomer == null && c.contains("NALCOGLOBALEQTSOLN")) {
                shipToCustomer = "Nalco Global Eqt Soln, Door 29"
                continue
            }

            if (addressLine2 == null && c.contains("DOOR29")) {
                addressLine2 = "Door 29"
                continue
            }

            if (addressLine1 == null && c.contains("6233WEST65THSTREET")) {
                addressLine1 = "6233 West 65th Street"
                continue
            }

            val csz = Regex(
                """^(CHICAGO),\s*(IL)\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (csz != null) {
                city = "CHICAGO"
                state = "IL"
                zip = csz.groupValues[3].trim()
                continue
            }

            if (city == null && state == null && zip == null && c.contains("CHICAGOIL60638")) {
                city = "CHICAGO"
                state = "IL"
                zip = "60638"
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull {
                compact(it).contains("NALCOGLOBALEQTSOLN")
            }?.let { "Nalco Global Eqt Soln, Door 29" }
        }

        if (addressLine1 == null) {
            addressLine1 = lines.firstOrNull {
                compact(it).contains("6233WEST65THSTREET")
            }?.let { "6233 West 65th Street" }
        }

        if (addressLine2 == null) {
            addressLine2 = lines.firstOrNull {
                compact(it).contains("DOOR29")
            }?.let { "Door 29" }
        }

        if (city == null || state == null || zip == null) {
            val cszLine = lines.firstOrNull { compact(it).contains("CHICAGOIL60638") }
            if (cszLine != null) {
                city = "CHICAGO"
                state = "IL"
                zip = "60638"
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

            // Example:
            // 10 ECL-56958.88-PRECISIONCHLORINESTRIPS 80 Mar11, 80 $68.00 BX(Box))
            val mainRow = Regex(
                """^\d+\s+([A-Z0-9.\-]+)-(.+?)\s+(\d+(?:,\d{3})?)\s+[A-Z][a-z]{2}\d{1,2},\s+(\d+(?:,\d{3})?)\s+\$[\d,]+\.\d{2}\s+[A-Z]{1,4}\(.+\)\)?$""",
                RegexOption.IGNORE_CASE
            ).find(current) ?: continue

            val poDescription = mainRow.groupValues[2].trim()
            val quantity = mainRow.groupValues[3].replace(",", "").toDoubleOrNull() ?: continue

            val next = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val skuMatch = Regex(
                """Supplier\s*Mat'?l\s*#:\s*([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(next) ?: continue

            val sku = normalizeSku(skuMatch.groupValues[1].trim().uppercase())

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