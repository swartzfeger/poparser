package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class KrowneLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "Krowne"

    override fun matches(lines: List<String>): Boolean {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()

        return joined.contains("KROWNE") &&
                joined.contains("PO NUMBER") &&
                joined.contains("VENDOR: SHIP TO:")
    }

    override fun score(lines: List<String>): Int {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()
        var score = 0

        if (joined.contains("KROWNE")) score += 30
        if (joined.contains("PO NUMBER")) score += 20
        if (joined.contains("VENDOR: SHIP TO:")) score += 30
        if (joined.contains("ATTN RECEIVING DOCK")) score += 10
        if (joined.contains("WHIPPANY")) score += 10

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        val orderNumber = findFirstMatch(
            textLines,
            Regex("""PO Number\s+([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        )

        val terms = findPaymentTerms(textLines)
        val shipTo = findKrowneShipTo(textLines)
        val items = findKrowneItems(textLines)

        return ParsedPdfFields(
            customerName = "KROWNE METAL CORPORATION",
            orderNumber = orderNumber,
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = terms,
            items = items
        )
    }

    private fun findPaymentTerms(lines: List<String>): String? {
        val termRegex = Regex("""\b(\d+%\s+\d+\s+NET\s+\d+|NET\s+\d+|COD|PREPAID)\b""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = termRegex.find(line)
            if (match != null) {
                return match.value.trim()
            }
        }

        return null
    }

    private fun findKrowneShipTo(lines: List<String>): InterpretedShipTo {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        for (line in lines) {
            val upper = line.uppercase()

            if (upper.contains("PRECISION LABORATORIES") && upper.contains("KROWNE")) {
                shipToCustomer = line.substringAfter("PRECISION LABORATORIES", "").trim()
                    .ifBlank { "KROWNE" }
            }

            if (upper.contains("ATTN RECEIVING DOCK")) {
                val attn = line.substringAfter("ATTN", "").trim()
                addressLine1 = if (attn.isNotBlank()) "ATTN $attn" else "ATTN RECEIVING DOCK 3 AND 4"
            }

            if (upper.contains("ALGONQUIN")) {
                addressLine2 = extractStreetAfterVendorZip(line)
            }

            if (upper.contains("WHIPPANY")) {
                val csz = extractCityStateZipAfterPhone(line)
                if (csz != null) {
                    city = csz.city
                    state = csz.state
                    zip = csz.zip
                }
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull { it.equals("Krowne", ignoreCase = true) } ?: "KROWNE"
        }

        return InterpretedShipTo(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )

    private fun extractCityStateZipAfterPhone(line: String): CityStateZip? {
        val phonePrefix = Regex("""^.*?\)\s*""")
        val trimmed = phonePrefix.replace(line, "").trim()

        val match = Regex("""([A-Za-z .'-]+),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")
            .find(trimmed) ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun extractStreetAfterVendorZip(line: String): String? {
        val match = Regex("""86326\s+(.+)$""").find(line)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun findKrowneItems(lines: List<String>) =
        buildList {
            val rowRegex = Regex(
                """^\d+\s+([A-Z0-9./-]+)\s+.+?\s+\d{1,2}/\d{1,2}/\d{4}\s+([\d,]+)\s+([\d,.]+)\s+([\d,.]+)$""",
                RegexOption.IGNORE_CASE
            )

            for (i in lines.indices.reversed()) {
                val rowMatch = rowRegex.find(lines[i]) ?: continue

                val sku = normalizeSku(rowMatch.groupValues[1].trim())
                val quantity = rowMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                val unitPrice = rowMatch.groupValues[3].replace(",", "").toDoubleOrNull()
                val description = ItemMapper.getItemDescription(sku).ifBlank { null }

                add(
                    0,
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