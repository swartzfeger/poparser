package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class BaileysTestStripsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Bailey's Test Strips"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("BAILEYSTESTSTRIPS") &&
                (
                        text.contains("THERMOMETERSLLC") ||
                                text.contains("69MAPLEAVE") ||
                                text.contains("HACKENSACKNJ07601") ||
                                text.contains("QAC4001V100")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("BAILEYSTESTSTRIPS")) score += 100
        if (text.contains("THERMOMETERSLLC")) score += 60
        if (text.contains("69MAPLEAVE")) score += 50
        if (text.contains("HACKENSACKNJ07601")) score += 60
        if (text.contains("ITEMDESCRIPTIONQTYRATEAMOUNT")) score += 40
        if (text.contains("QAC4001V100")) score += 70

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)
        val customerName = shipTo.shipToCustomer ?: parseCustomerName(clean)
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
            compact(it).contains("BAILEYSTESTSTRIPS") && compact(it).contains("THERMOMETERSLLC")
        }?.let { "BAILEY'S TEST STRIPS & THERMOMETERS, LLC" }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactCurrent = compact(current)

            val inline = Regex(
                """P\.?\s*O\.?\s*(?:NO|NUMBER)?\.?\s*[:#]?\s*(\d{4,10})""",
                RegexOption.IGNORE_CASE
            ).find(current)
            if (inline != null) {
                return inline.groupValues[1].trim()
            }

            if (compactCurrent.contains("DATEPONO") || compactCurrent.contains("DATEPONO.")) {
                for (j in (i + 1)..minOf(i + 2, lines.lastIndex)) {
                    val candidate = lines[j].replace(Regex("""\s+"""), " ").trim()

                    val dateThenPo = Regex(
                        """^\d{1,2}/\d{1,2}/\d{4}\s+(\d{4,10})$"""
                    ).find(candidate)
                    if (dateThenPo != null) {
                        return dateThenPo.groupValues[1]
                    }

                    val tokens = candidate.split(" ")
                    if (tokens.size >= 2 &&
                        tokens.first().matches(Regex("""\d{1,2}/\d{1,2}/\d{4}""")) &&
                        tokens.last().matches(Regex("""\d{4,10}"""))
                    ) {
                        return tokens.last()
                    }
                }
            }

            if (
                compactCurrent == "PONO" ||
                compactCurrent == "PONO." ||
                compactCurrent == "PONUMBER" ||
                compactCurrent == "PONUMBER."
            ) {
                for (j in (i + 1)..minOf(i + 3, lines.lastIndex)) {
                    val candidate = lines[j].replace(Regex("""\s+"""), " ").trim()
                    if (candidate.matches(Regex("""\d{4,10}"""))) {
                        return candidate
                    }
                }
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val shipIndex = lines.indexOfFirst { compact(it) == "SHIPTO" }

        if (shipIndex >= 0) {
            val window = lines.drop(shipIndex + 1).take(6)

            val shipToCustomer = window
                .firstOrNull {
                    compact(it).contains("BAILEYSTESTSTRIPS") && compact(it).contains("THERMOMETERSLLC")
                }
                ?.let { "BAILEY'S TEST STRIPS & THERMOMETERS, LLC" }

            val addressLine1 = window
                .firstOrNull { compact(it).contains("69MAPLEAVE") }
                ?.let { "69 MAPLE AVE" }

            val cityStateZipLine = window.firstOrNull {
                compact(it).contains("HACKENSACKNJ07601")
            }

            val parsed = parseCityStateZip(cityStateZipLine)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                addressLine2 = null,
                city = parsed.city,
                state = parsed.state,
                zip = parsed.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = parseCustomerName(lines),
            addressLine1 = lines.firstOrNull { compact(it).contains("69MAPLEAVE") }?.let { "69 MAPLE AVE" },
            addressLine2 = null,
            city = lines.firstOrNull { compact(it).contains("HACKENSACKNJ07601") }?.let { "HACKENSACK" },
            state = lines.firstOrNull { compact(it).contains("HACKENSACKNJ07601") }?.let { "NJ" },
            zip = lines.firstOrNull { compact(it).contains("HACKENSACKNJ07601") }?.let { "07601" }
        )
    }

    private fun parseItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^[A-Z0-9]+\s+\*{3}([A-Z0-9-]+)\*{3}(.+?)\s+(\d+(?:,\d{3})*(?:\.\d+)?)\s+(\d+(?:,\d{3})*\.\d{2})\s+(\d+(?:,\d{3})*\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val rawSku = rowMatch.groupValues[1].trim().uppercase()
            val firstDescPart = rowMatch.groupValues[2].trim()
            val quantity = rowMatch.groupValues[3].replace(",", "").toDoubleOrNull()
            val unitPrice = rowMatch.groupValues[4].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val nextLine = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val fullDescription = buildString {
                append(firstDescPart)
                if (nextLine.isNotBlank()
                    && !nextLine.startsWith("$")
                    && !compact(nextLine).contains("TOTAL")
                    && !looksLikeAnotherItemRow(nextLine)
                ) {
                    append(" ")
                    append(nextLine)
                }
            }.trim()

            val sku = normalizeSku(rawSku)
            val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { fullDescription }

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            add(
                item(
                    sku = sku,
                    description = mappedDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun looksLikeAnotherItemRow(line: String): Boolean {
        val normalized = line.replace(Regex("""\s+"""), " ").trim()
        return Regex(
            """^[A-Z0-9]+\s+\*{3}[A-Z0-9-]+\*{3}.+\s+\d+(?:,\d{3})*(?:\.\d+)?\s+\d+(?:,\d{3})*\.\d{2}\s+\d+(?:,\d{3})*\.\d{2}$""",
            RegexOption.IGNORE_CASE
        ).matches(normalized)
    }

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val cleaned = line
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = Regex(
            """^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim(),
                state = match.groupValues[2].trim().uppercase(),
                zip = match.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, null)
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

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}