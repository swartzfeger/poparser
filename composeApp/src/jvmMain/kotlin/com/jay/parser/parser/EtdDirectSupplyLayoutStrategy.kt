package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class EtdDirectSupplyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "ETD Direct Supply"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("ETDDIRECTSUPPLY") &&
                (
                        text.contains("803380") ||
                                text.contains("1REDOAKDRIVE") ||
                                text.contains("PLAISTOWNH03865") ||
                                text.contains("PER1001VCC1002")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("ETDDIRECTSUPPLYLLC")) score += 100
        if (text.contains("PONO803380")) score += 80
        if (text.contains("1REDOAKDRIVE")) score += 60
        if (text.contains("PLAISTOWNH03865")) score += 60
        if (text.contains("PER1001VCC1002")) score += 80
        if (text.contains("LIQUIDSAMPLETESTSTRIPS100CT")) score += 30

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
            compact(it).contains("ETDDIRECTSUPPLYLLC")
        }?.let { "ETD Direct Supply, LLC" }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactCurrent = compact(current)

            val inline = Regex(
                """P\.?\s*O\.?\s*No\.?\s*[:#]?\s*([A-Z0-9-]+)""",
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

            if (compactCurrent == "PONO" || compactCurrent == "PONO.") {
                for (j in (i + 1)..minOf(i + 2, lines.lastIndex)) {
                    val candidate = lines[j].trim()
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
            val window = lines.drop(shipIndex + 1).take(8)

            val shipToCustomer = window.firstOrNull {
                compact(it).contains("ETDDIRECTSUPPLYLLC")
            }?.let { "ETD Direct Supply, LLC" }

            val addressLines = window.map { it.replace(Regex("""\s+"""), " ").trim() }

            val addressLine1 = when {
                addressLines.any { compact(it).contains("1REDOAKDRIVEUNITB") } -> "1 Red Oak Drive, Unit B"
                addressLines.any { compact(it) == "1REDOAKDRIVE" } &&
                        addressLines.any { compact(it) == "UNITB" } -> "1 Red Oak Drive"
                else -> addressLines.firstOrNull {
                    compact(it).contains("1REDOAKDRIVE")
                }
            }

            val addressLine2 = when {
                addressLines.any { compact(it).contains("1REDOAKDRIVEUNITB") } -> null
                addressLines.any { compact(it) == "UNITB" } -> "Unit B"
                else -> null
            }

            val cszLine = addressLines.firstOrNull {
                compact(it).contains("PLAISTOWNH03865")
            }

            val parsed = parseCityStateZip(cszLine)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = parsed.city,
                state = parsed.state,
                zip = parsed.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = parseCustomerName(lines),
            addressLine1 = lines.firstOrNull { compact(it).contains("1REDOAKDRIVEUNITB") }?.let { "1 Red Oak Drive, Unit B" }
                ?: lines.firstOrNull { compact(it) == "1REDOAKDRIVE" }?.let { "1 Red Oak Drive" },
            addressLine2 = lines.firstOrNull { compact(it) == "UNITB" }?.let { "Unit B" },
            city = lines.firstOrNull { compact(it).contains("PLAISTOWNH03865") }?.let { "Plaistow" },
            state = lines.firstOrNull { compact(it).contains("PLAISTOWNH03865") }?.let { "NH" },
            zip = lines.firstOrNull { compact(it).contains("PLAISTOWNH03865") }?.let { "03865" }
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

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val cleaned = line.replace(Regex("""\s+"""), " ").trim()

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