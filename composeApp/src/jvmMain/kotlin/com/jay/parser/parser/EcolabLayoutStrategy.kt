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
                                text.contains("SUPPLIERMATL") ||
                                text.contains("2152RIVERBENDWESTDRIVE")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("ECOLABPRODUCTIONLLC")) score += 100
        if (text.contains("PONUMBER5504330257")) score += 90
        if (text.contains("FOODSAFETYSPECIALTIESUS75")) score += 80
        if (text.contains("2152RIVERBENDWESTDRIVE")) score += 70
        if (text.contains("FORTWORTHTX76118")) score += 70
        if (text.contains("PAYMENTTERMSDUEIN60DAYSEOM1D")) score += 40
        if (text.contains("SUPPLIERMATL")) score += 60
        if (text.contains("CLK50V100")) score += 40
        if (text.contains("CHL30025V100")) score += 40

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
        }

        val joined = lines.joinToString(" ")
        return Regex(
            """PO\s*Number\s*[:#]?\s*(\d{8,12})""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.get(1)?.trim()
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

            if (addressLine1 == null && compactLine.contains("2152RIVERBENDWESTDRIVE")) {
                addressLine1 = "2152 Riverbend West Drive"
                continue
            }

            val cszMatch = Regex(
                """^(Fort\s+Worth),\s*(TX)\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (cszMatch != null) {
                city = "Fort Worth"
                state = "TX"
                zip = cszMatch.groupValues[3].trim()
                continue
            }

            if (city == null && state == null && zip == null && compactLine.contains("FORTWORTHTX76118")) {
                city = "Fort Worth"
                state = "TX"
                zip = "76118"
            }
        }

        if (shipToCustomer == null) {
            shipToCustomer = lines.firstOrNull {
                compact(it).contains("FOODSAFETYSPECIALTIESUS75")
            }?.let { "Food Safety Specialties US75" }
        }

        if (addressLine1 == null) {
            addressLine1 = lines.firstOrNull {
                compact(it).contains("2152RIVERBENDWESTDRIVE")
            }?.let { "2152 Riverbend West Drive" }
        }

        if (city == null || state == null || zip == null) {
            val cszLine = lines.firstOrNull {
                compact(it).contains("FORTWORTHTX76118")
            }

            if (cszLine != null) {
                city = "Fort Worth"
                state = "TX"
                zip = "76118"
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
                """Supplier\s*Mat.?l\s*#:\s*([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(current) ?: continue

            val sku = normalizeSku(skuMatch.groupValues[1].trim().uppercase())

            var quantity: Double? = null
            var unitPrice: Double? = null
            var description: String? = null

            for (j in (i - 1) downTo maxOf(0, i - 3)) {
                val row = lines[j].replace(Regex("""\s+"""), " ").trim()
                if (row.isBlank()) continue
                if (row.contains("Supplier", ignoreCase = true)) continue
                if (row.contains("Notes", ignoreCase = true)) continue

                val rowMatch = Regex(
                    """^\d+\s+([A-Z0-9-]+)\s+(\d+(?:,\d{3})?)\s+[A-Z][a-z]{2}\d{1,2},\s+(\d+(?:,\d{3})?)\s+\$([\d,]+\.\d{2}).*$""",
                    RegexOption.IGNORE_CASE
                ).find(row)

                if (rowMatch != null) {
                    description = rowMatch.groupValues[1].trim()
                    quantity = rowMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                    unitPrice = rowMatch.groupValues[4].replace(",", "").toDoubleOrNull()
                    break
                }

                val fallbackQty = Regex("""\s(\d+(?:,\d{3})?)\s+[A-Z][a-z]{2}\d{1,2},""").find(row)
                val fallbackPrice = Regex("""\$([\d,]+\.\d{2})""").find(row)
                val fallbackDesc = Regex("""^\d+\s+([A-Z0-9-]+)""").find(row)

                if (fallbackQty != null && fallbackPrice != null && fallbackDesc != null) {
                    description = fallbackDesc.groupValues[1].trim()
                    quantity = fallbackQty.groupValues[1].replace(",", "").toDoubleOrNull()
                    unitPrice = fallbackPrice.groupValues[1].replace(",", "").toDoubleOrNull()
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