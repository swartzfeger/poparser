package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class EcolabLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "ECOLAB"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("ECOLAB") &&
                (
                        text.contains("PONUMBER") ||
                                text.contains("FOODSAFETYSPECIALTIESUS75") ||
                                text.contains("ECOLABGATEWAYEQUIPECCCUS82") ||
                                text.contains("SUPPLIERMATL") ||
                                text.contains("2152RIVERBENDWESTDRIVE") ||
                                text.contains("1630APEXDR")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("ECOLABPRODUCTIONLLC")) score += 100
        if (text.contains("PONUMBER")) score += 60
        if (text.contains("FOODSAFETYSPECIALTIESUS75")) score += 80
        if (text.contains("ECOLABGATEWAYEQUIPECCCUS82")) score += 80
        if (text.contains("2152RIVERBENDWESTDRIVE")) score += 70
        if (text.contains("1630APEXDR")) score += 70
        if (text.contains("FORTWORTHTX76118")) score += 70
        if (text.contains("BELOITWI53511")) score += 70
        if (text.contains("PAYMENTTERMSDUEIN60DAYSEOM1D")) score += 40
        if (text.contains("SUPPLIERMATL")) score += 60
        if (text.contains("CLK50V100")) score += 40
        if (text.contains("CHL30025V100")) score += 40
        if (text.contains("LAC1V100")) score += 40

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
            terms = mappedCustomer?.terms ?: parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull { compact(it).contains("ECOLABPRODUCTIONLLC") }
            ?.let { "Ecolab Production LLC" }
            ?: lines.firstOrNull { it.contains("Ecolab Production", ignoreCase = true) }?.trim()
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

            val compactLine = compact(normalized)
            Regex("""PONUMBER(\d{8,12})""")
                .find(compactLine)
                ?.let { return it.groupValues[1].trim() }
        }

        val joined = lines.joinToString(" ")
        Regex(
            """PO\s*Number\s*[:#]?\s*(\d{8,12})""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.let { return it.groupValues[1].trim() }

        return Regex("""PONUMBER(\d{8,12})""")
            .find(compact(joined))
            ?.groupValues
            ?.get(1)
            ?.trim()
    }

    private fun parseTerms(lines: List<String>): String? {
        for (i in lines.indices) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactCurrent = compact(current)

            if (compactCurrent.contains("PAYMENTTERMS")) {
                val sameLine = current
                    .replace(Regex("""(?i)payment\s*terms\s*[:#]?\s*"""), "")
                    .trim()

                if (sameLine.isNotBlank() && !sameLine.equals(current, ignoreCase = true)) {
                    return normalizeTerms(sameLine)
                }

                val next = lines.getOrNull(i + 1)
                    ?.replace(Regex("""\s+"""), " ")
                    ?.trim()

                if (!next.isNullOrBlank()) {
                    return normalizeTerms(next)
                }
            }
        }

        val fallback = lines.firstOrNull {
            compact(it).contains("DUEIN60DAYSEOM1D")
        }?.replace(Regex("""\s+"""), " ")?.trim()

        return fallback?.let { normalizeTerms(it) }
    }

    private fun normalizeTerms(raw: String): String {
        val cleaned = raw
            .replace(Regex("""(?i)payment\s*terms\s*[:#]?\s*"""), "")
            .trim()

        return when (compact(cleaned)) {
            "DUEIN60DAYSEOM1D" -> "Due in 60 Days EOM +1D"
            else -> cleaned
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val fullCompact = compact(lines.joinToString(" "))

        if (fullCompact.contains("ECOLABGATEWAYEQUIPECCCUS82") ||
            fullCompact.contains("1630APEXDR") ||
            fullCompact.contains("BELOITWI53511")
        ) {
            return ShipToBlock(
                shipToCustomer = "Ecolab - Gateway Equip ECCC US82",
                addressLine1 = "1630 APEX DR",
                addressLine2 = null,
                city = "BELOIT",
                state = "WI",
                zip = "53511"
            )
        }

        if (fullCompact.contains("FOODSAFETYSPECIALTIESUS75") ||
            fullCompact.contains("2152RIVERBENDWESTDRIVE") ||
            fullCompact.contains("FORTWORTHTX76118")
        ) {
            return ShipToBlock(
                shipToCustomer = "Food Safety Specialties US75",
                addressLine1 = "2152 Riverbend West Drive",
                addressLine2 = null,
                city = "Fort Worth",
                state = "TX",
                zip = "76118"
            )
        }

        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipIndex = lines.indexOfFirst { compact(it).contains("SHIPTOADDRESS") }

        val searchWindow = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(12)
        } else {
            lines
        }

        for (line in searchWindow) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(trimmed)

            if (shipToCustomer == null && compactLine.contains("FOODSAFETYSPECIALTIESUS75")) {
                shipToCustomer = "Food Safety Specialties US75"
                continue
            }

            if (shipToCustomer == null && compactLine.contains("ECOLABGATEWAYEQUIPECCCUS82")) {
                shipToCustomer = "Ecolab - Gateway Equip ECCC US82"
                continue
            }

            if (addressLine1 == null && compactLine.contains("2152RIVERBENDWESTDRIVE")) {
                addressLine1 = "2152 Riverbend West Drive"
                continue
            }

            if (addressLine1 == null && compactLine.contains("1630APEXDR")) {
                addressLine1 = "1630 APEX DR"
                continue
            }

            val cszMatch = Regex(
                """^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (cszMatch != null) {
                city = normalizeCity(cszMatch.groupValues[1].trim())
                state = cszMatch.groupValues[2].uppercase().trim()
                zip = cszMatch.groupValues[3].trim()
                continue
            }

            if (city == null && state == null && zip == null && compactLine.contains("FORTWORTHTX76118")) {
                city = "Fort Worth"
                state = "TX"
                zip = "76118"
            }

            if (city == null && state == null && zip == null && compactLine.contains("BELOITWI53511")) {
                city = "BELOIT"
                state = "WI"
                zip = "53511"
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

        for (i in lines.indices) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()

            val skuMatch = Regex(
                """Supplier\s*Mat.?l\s*#:\s*(?:VENDOR#\s*)?([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(current) ?: continue

            val sku = normalizeSku(skuMatch.groupValues[1].trim().uppercase())

            var quantity: Double? = null
            var unitPrice: Double? = null
            var description: String? = null

            for (j in (i - 1) downTo maxOf(0, i - 4)) {
                val row = lines[j].replace(Regex("""\s+"""), " ").trim()
                if (row.isBlank()) continue
                if (row.contains("Supplier", ignoreCase = true)) continue
                if (row.contains("Notes", ignoreCase = true)) continue
                if (row.contains("Description", ignoreCase = true) && row.contains("Item", ignoreCase = true)) continue

                val rowMatch = Regex(
                    """^\d+\s+(.+?)\s+(\d{1,3}(?:,\d{3})+|\d+)\s+[A-Z][a-z]{2}\s*\d{1,2},\s+(\d{1,3}(?:,\d{3})+|\d+)\s+\$([\d,]+\.\d{2})(?:\s+(\d{1,3}(?:,\d{3})+|\d+))?.*$""",
                    RegexOption.IGNORE_CASE
                ).find(row)

                if (rowMatch != null) {
                    description = rowMatch.groupValues[1].trim()
                    quantity = rowMatch.groupValues[2].replace(",", "").toDoubleOrNull()

                    val price = rowMatch.groupValues[4].replace(",", "").toDoubleOrNull()
                    val per = rowMatch.groupValues.getOrNull(5)
                        ?.replace(",", "")
                        ?.toDoubleOrNull()

                    unitPrice = if (price != null && per != null && per > 1.0) {
                        price / per
                    } else {
                        price
                    }
                    break
                }

                val fallbackQty = Regex("""\s(\d{1,3}(?:,\d{3})+|\d+)\s+[A-Z][a-z]{2}\s*\d{1,2},""").find(row)
                val fallbackPrice = Regex("""\$([\d,]+\.\d{2})(?:\s+(\d{1,3}(?:,\d{3})+|\d+))?""").find(row)
                val fallbackDesc = Regex("""^\d+\s+(.+?)\s+(\d{1,3}(?:,\d{3})+|\d+)\s+[A-Z][a-z]{2}""").find(row)

                if (fallbackQty != null && fallbackPrice != null && fallbackDesc != null) {
                    description = fallbackDesc.groupValues[1].trim()
                    quantity = fallbackQty.groupValues[1].replace(",", "").toDoubleOrNull()

                    val price = fallbackPrice.groupValues[1].replace(",", "").toDoubleOrNull()
                    val per = fallbackPrice.groupValues.getOrNull(2)
                        ?.replace(",", "")
                        ?.toDoubleOrNull()

                    unitPrice = if (price != null && per != null && per > 1.0) {
                        price / per
                    } else {
                        price
                    }
                    break
                }
            }

            if (quantity == null || unitPrice == null) continue

            val finalDescription = ItemMapper.getItemDescription(sku).ifBlank {
                description?.ifBlank { sku } ?: sku
            }

            val key = "$sku|$quantity|$unitPrice"
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

    private fun normalizeCity(value: String): String {
        return when (compact(value)) {
            "FORTWORTH" -> "Fort Worth"
            "BELOIT" -> "BELOIT"
            else -> value
        }
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
