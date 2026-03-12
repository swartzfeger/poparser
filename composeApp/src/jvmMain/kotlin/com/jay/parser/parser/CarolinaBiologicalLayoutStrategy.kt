package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class CarolinaBiologicalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Carolina Biological"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("CAROLINA BIOLOGICAL") &&
                (
                        text.contains("WHITSETT") ||
                                text.contains("GLU-1B-100") ||
                                text.contains("CBS ORDER #") ||
                                text.contains("P.O. NUMBER")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("CAROLINA BIOLOGICAL")) score += 100
        if (text.contains("WHITSETT")) score += 60
        if (text.contains("GLU-1B-100")) score += 60
        if (text.contains("P.O. NUMBER")) score += 40
        if (text.contains("CBS ORDER #")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findCarolinaShipTo(textLines)

        return ParsedPdfFields(
            customerName = findCustomerName(textLines),
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findCarolinaItems(textLines)
        )
    }

    private fun findCustomerName(textLines: List<String>): String? {
        val mergedLine = textLines.firstOrNull {
            it.contains("CAROLINA BIOLOGICAL SUPPLY", ignoreCase = true)
        } ?: return null

        return extractRightSideCustomerName(mergedLine)
            ?: "CAROLINA BIOLOGICAL SUPPLY - BP7"
    }

    private fun findOrderNumber(textLines: List<String>): String? {
        return findFirstMatch(
            textLines,
            Regex("""P\.?O\.?\s*NUMBER\s+(\d{4,})""", RegexOption.IGNORE_CASE)
        ) ?: findFirstMatch(
            textLines,
            Regex("""PO#:\s*(\d{4,})""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(textLines: List<String>): String? {
        val text = textLines.joinToString(" ").uppercase().replace(" ", "")
        return if (text.contains("TERMS:1%10NET30") || text.contains("1%10NET30")) {
            "1% 10 NET 30"
        } else {
            null
        }
    }

    private fun findCarolinaShipTo(textLines: List<String>): ShipToBlock {
        val mergedCustomerLine = textLines.firstOrNull {
            it.contains("CAROLINA BIOLOGICAL SUPPLY", ignoreCase = true)
        }

        val shipToCustomer = mergedCustomerLine
            ?.let { extractRightSideCustomerName(it) }
            ?: "CAROLINA BIOLOGICAL SUPPLY - BP7"

        val addressLine2 = textLines.firstOrNull {
            it.contains("ATTN:", ignoreCase = true)
        }?.let { cleanAttn(it) }

        val addressLine1 = textLines.firstOrNull {
            it.contains("JUDGE ADAMS", ignoreCase = true)
        }?.trim()

        val cityStateZipLine = textLines.firstOrNull {
            it.contains("WHITSETT", ignoreCase = true) && it.contains("27377")
        }?.replace("WHITSETTNC", "WHITSETT NC")

        val parsed = parseCityStateZip(cityStateZipLine)

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = parsed.city,
            state = parsed.state,
            zip = parsed.zip
        )
    }

    private fun findCarolinaItems(lines: List<String>) =
        buildList {
            for (line in lines) {
                val normalized = line.replace(Regex("""\s+"""), " ").trim()

                // Actual observed line:
                // 1 893840 GLU-1B-100 TESTSTRIP GLUCOSE PK/100 100 EA 9.5000 950.00 01/26/26
                val match = Regex(
                    """^\d+\s+\d+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+EA\s+(\d+(?:\.\d{1,4})?)\s+\d+(?:\.\d{2})\s+\d{2}/\d{2}/\d{2}$""",
                    RegexOption.IGNORE_CASE
                ).find(normalized) ?: continue

                val sku = normalizeSku(match.groupValues[1].trim())
                val description = match.groupValues[2].trim()
                val quantity = match.groupValues[3].toDoubleOrNull()
                val unitPrice = match.groupValues[4].toDoubleOrNull()

                add(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku).ifBlank {
                            description.ifBlank { null }
                        },
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

    private fun extractRightSideCustomerName(line: String): String? {
        val upper = line.uppercase()
        val marker = "PRECISION LABORATORIES"
        val idx = upper.indexOf(marker)

        val remainder = if (idx >= 0) {
            line.substring(idx + marker.length).trim()
        } else {
            line.trim()
        }

        return remainder
            .replace(Regex("""\s*-\s*BP7$""", RegexOption.IGNORE_CASE), " - BP7")
            .ifBlank { null }
    }

    private fun cleanAttn(line: String): String {
        return line
            .replace("KITTINGDEPT", "KITTING DEPT", ignoreCase = true)
            .trim()
    }

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val normalized = line.trim().replace("WHITSETTNC", "WHITSETT NC")

        val match = Regex("""^(.*)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""").find(normalized)
        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim(),
                state = match.groupValues[2].trim(),
                zip = match.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, null)
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

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}