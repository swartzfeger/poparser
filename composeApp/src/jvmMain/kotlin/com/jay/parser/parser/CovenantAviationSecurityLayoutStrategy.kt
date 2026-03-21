package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class CovenantAviationSecurityLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Covenant Aviation Security"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("COVENANTAVIATION") &&
                (
                        text.contains("CAS549650WJR") ||
                                text.contains("MALLORYSAFETY") ||
                                text.contains("TSAPER100") ||
                                text.contains("BOLINGBROOKIL60440")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("COVENANTAVIATIONSECURITYLLC")) score += 100
        if (text.contains("CAS549650WJR")) score += 90
        if (text.contains("1112WBOUGHTONROAD")) score += 60
        if (text.contains("BOLINGBROOKIL60440")) score += 60
        if (text.contains("MALLORYSAFETY") || text.contains("MALLORYSAFETYSUPPLYLLC")) score += 50
        if (text.contains("44380OSGOODROAD")) score += 50
        if (text.contains("FREMONTCA94539")) score += 50
        if (text.contains("TSAPER100")) score += 80
        if (text.contains("TSAPLASTICSTRIPS")) score += 40

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
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("COVENANTAVIATIONSECURITYLLC")
        }?.let { "COVENANT AVIATION SECURITY, LLC" }
            ?: lines.firstOrNull {
                compact(it).contains("COVENANTAVIATIONSECURITY")
            }?.trim()
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """PO\s*#?\s*[:\-]?\s*([A-Z0-9-]+\s+[A-Z0-9]+|[A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(normalized)
            if (inline != null) {
                return inline.groupValues[1].trim()
            }

            if (normalized.matches(Regex("""CAS-\d+\s+[A-Z0-9]+""", RegexOption.IGNORE_CASE))) {
                return normalized
            }
        }

        val joined = lines.joinToString(" ")
        return Regex("""CAS-\d+\s+[A-Z0-9]+""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipIndex = lines.indexOfFirst { compact(it).contains("SHIPTO") }

        val window = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(8)
        } else {
            lines
        }

        for (line in window) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val c = compact(trimmed)

            if (shipToCustomer == null && c.contains("MALLORYSAFETYSUPPLYLLC")) {
                shipToCustomer = "MALLORY SAFETY & SUPPLY LLC"
                continue
            }

            if (addressLine1 == null && c.contains("44380OSGOODROAD")) {
                addressLine1 = "44380 OSGOOD ROAD"
                continue
            }

            val csz = Regex(
                """^(FREMONT),\s*(CA)\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (csz != null) {
                city = "FREMONT"
                state = "CA"
                zip = csz.groupValues[3].trim()
                continue
            }

            if (city == null && state == null && zip == null && c.contains("FREMONTCA94539")) {
                city = "FREMONT"
                state = "CA"
                zip = "94539"
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull {
                compact(it).contains("MALLORYSAFETYSUPPLYLLC")
            }?.let { "MALLORY SAFETY & SUPPLY LLC" }
        }

        if (addressLine1 == null) {
            addressLine1 = lines.firstOrNull {
                compact(it).contains("44380OSGOODROAD")
            }?.let { "44380 OSGOOD ROAD" }
        }

        if (city == null || state == null || zip == null) {
            val cszLine = lines.firstOrNull { compact(it).contains("FREMONTCA94539") }
            if (cszLine != null) {
                city = "FREMONT"
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

    private fun parseItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val normalized = lines[i].replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^(PER-100-1V-)\s*([A-Z0-9-]+)\s+(.+?)\s+(\d+(?:,\d{3})?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(normalized) ?: continue

            val skuBase = match.groupValues[1].trim()
            val codeAfterBase = match.groupValues[2].trim()
            val description = match.groupValues[3].trim()
            val quantity = match.groupValues[4].replace(",", "").toDoubleOrNull()
            val unitPrice = match.groupValues[5].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            // ETD layout:
            // PER-100-1V- CC1002 Liquid Sample Test Strips, 100ct 450 9.90 4,455.00
            // 100
            //
            // The real SKU is PER-100-1V-100, not PER-100-1V-CC1002.
            val nextLine = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val sku = when {
                nextLine.matches(Regex("""^\d{2,4}$""")) ->
                    normalizeSku("$skuBase$nextLine")
                else ->
                    normalizeSku(skuBase + codeAfterBase)
            }

            val finalDescription = ItemMapper.getItemDescription(sku).ifBlank { description }

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            add(
                item(
                    sku = sku,
                    description = finalDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
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