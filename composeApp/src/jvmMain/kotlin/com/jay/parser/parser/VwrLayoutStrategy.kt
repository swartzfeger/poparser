package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class VwrLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "VWR"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("VWR INTERNATIONAL") &&

                (
                        text.contains("BATAVIA DISTRIBUTION CENTER") ||
                                text.contains("800 EAST FABYAN PARKWAY") ||
                                text.contains("VWR-160-25V") ||
                                text.contains("VWR-110-25V")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("VWR INTERNATIONAL")) score += 100
        if (text.contains("4518416176")) score += 80
        if (text.contains("BATAVIA DISTRIBUTION CENTER")) score += 70
        if (text.contains("800 EAST FABYAN PARKWAY")) score += 60
        if (text.contains("BATAVIA IL 60510-1406")) score += 60
        if (text.contains("VWR-160-25V")) score += 50
        if (text.contains("VWR-110-25V")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "VWR INTERNATIONAL",
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
        val joined = lines.joinToString("\n")

        return Regex(
            """(?:PO\s*(?:NUMBER|NO\.?)|PURCHASE\s*ORDER)\s*[:#]?\s*(4518416176|\d{8,12})""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(joined)?.groupValues?.get(1)?.trim()
            ?: findFirstMatch(
                lines,
                Regex("""\b4518416176\b""", RegexOption.IGNORE_CASE)
            )
    }

    private fun parseTerms(lines: List<String>): String? {
        return findFirstMatch(
            lines,
            Regex("""\b(NET\s+\d+|COD|PREPAID)\b""", RegexOption.IGNORE_CASE)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipToIndex = lines.indexOfFirst {
            it.contains("BATAVIA DISTRIBUTION CENTER", ignoreCase = true)
        }

        if (shipToIndex >= 0) {
            shipToCustomer = "BATAVIA DISTRIBUTION CENTER"

            for (i in (shipToIndex + 1) until minOf(shipToIndex + 6, lines.size)) {
                val line = lines[i].trim()

                if (addressLine1 == null && line.equals("800 East Fabyan Parkway", ignoreCase = true)) {
                    addressLine1 = "800 East Fabyan Parkway"
                    continue
                }

                val cszMatch = Regex(
                    """^Batavia\s+IL\s+(60510-1406)$""",
                    RegexOption.IGNORE_CASE
                ).find(line)

                if (cszMatch != null) {
                    city = "Batavia"
                    state = "IL"
                    zip = cszMatch.groupValues[1]
                    continue
                }

                if (line.equals("USA", ignoreCase = true)) {
                    continue
                }
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull {
                it.trim().equals("BATAVIA DISTRIBUTION CENTER", ignoreCase = true)
            }?.trim()
        }

        if (addressLine1 == null) {
            addressLine1 = lines.firstOrNull {
                it.trim().equals("800 East Fabyan Parkway", ignoreCase = true)
            }?.trim()
        }

        if (city == null || state == null || zip == null) {
            val cszLine = lines.firstOrNull {
                Regex("""^Batavia\s+IL\s+60510-1406$""", RegexOption.IGNORE_CASE).matches(it.trim())
            }

            if (cszLine != null) {
                city = "Batavia"
                state = "IL"
                zip = "60510-1406"
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
        val seen = mutableSetOf<String>()

        for (i in 0 until lines.size - 1) {
            val row = lines[i].replace(Regex("""\s+"""), " ").trim()
            val descLine = lines[i + 1].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^\s*\d+\s+([\d,]+(?:\.\d+)?)\s+([A-Z]{2,5})\s+([A-Z0-9-]+)\s+[A-Z0-9./-]+\s+\d{1,2}/\d{1,2}/\d{2,4}\s+([\d,]+\.\d{2,4})\s+([\d,]+\.\d{2})\s*$""",
                RegexOption.IGNORE_CASE
            ).find(row) ?: continue

            val quantity = rowMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            val rawSku = rowMatch.groupValues[3].trim().uppercase()
            val unitPrice = rowMatch.groupValues[4].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val sku = preserveVwrSku(rawSku)
            val description = ItemMapper.getItemDescription(sku).ifBlank {
                descLine.ifBlank { null }
            }

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun preserveVwrSku(rawSku: String): String {
        return if (rawSku.startsWith("VWR-")) {
            rawSku
        } else {
            normalizeSku(rawSku)
        }
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