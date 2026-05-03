package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.round

class AquaPhoenixScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "AQUA PHOENIX SCIENTIFIC"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("AQUAPHOENIX") ||
                text.contains("AQUAPHOENIXSCIENTIFIC") ||
                text.contains("AQUAPHOENIXSCI")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0

        if (text.contains("AQUAPHOENIX")) score += 120
        if (text.contains("AQUAPHOENIXSCIENTIFIC")) score += 100
        if (text.contains("860GITTSRUNROAD")) score += 80
        if (text.contains("HANOVERPA17331")) score += 80
        if (text.contains("ACHIN30DAYS")) score += 70
        if (text.contains("VENDORSHIPTO")) score += 60
        if (text.contains("PURCHASEORDERNO")) score += 50
        if (text.contains("PH01141B50") || text.contains("PH01141B50")) score += 40
        if (text.contains("PHO1V50")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { normalize(it) }
        val joined = clean.joinToString(" ")
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "AQUA PHOENIX SCIE",
            orderNumber = parseOrderNumber(joined),
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

    private fun parseOrderNumber(joined: String): String? {
        Regex("""Purchase\s+Order\s*-\s*(PO\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.uppercase()
            ?.let { return it }

        Regex("""Purchase\s+Order\s+No\.?\s*(PO\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.uppercase()
            ?.let { return it }

        Regex("""\bPO\d{6,12}\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.uppercase()
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        for (line in lines) {
            Regex("""Payment\s+Terms:\s*(.+)$""", RegexOption.IGNORE_CASE)
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.let { value ->
                    if (value.contains("ACH", ignoreCase = true) && value.contains("30", ignoreCase = true)) {
                        return "ACH in 30 Days"
                    }
                    return value
                }

            if (line.contains("ACH in 30 Days", ignoreCase = true)) {
                return "ACH in 30 Days"
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst {
            it.contains("VENDOR", ignoreCase = true) &&
                    it.contains("SHIP TO", ignoreCase = true)
        }

        if (idx >= 0) {
            val customerLine = lines.getOrNull(idx + 1).orEmpty()
            val addressLine = lines.getOrNull(idx + 2).orEmpty()
            val cityLine = lines.getOrNull(idx + 3).orEmpty()

            val shipToCustomer = splitVendorShipToLine(customerLine).second
                ?: "AquaPhoenix Scientific PA"

            val address1 = splitVendorShipToLine(addressLine).second
                ?: "860 Gitts Run Road"

            val cityStateZipText = splitVendorShipToLine(cityLine).second
                ?: "Hanover, PA 17331"

            val parsed = parseCityStateZip(cityStateZipText)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = address1,
                addressLine2 = null,
                city = parsed?.city,
                state = parsed?.state,
                zip = parsed?.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = "AquaPhoenix Scientific PA",
            addressLine1 = "860 Gitts Run Road",
            addressLine2 = null,
            city = "Hanover",
            state = "PA",
            zip = "17331"
        )
    }

    private fun splitVendorShipToLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val vendorPrefixes = listOf(
            "Vendor No. S452",
            "Vendor No. $452",
            "Precision Laboratories",
            "415 Airpark Drive",
            "Cottonwood, AZ 86326"
        )

        for (prefix in vendorPrefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val right = trimmed.removePrefix(prefix).trim()
                return prefix to right.ifBlank { null }
            }
        }

        val parts = Regex("""\s{2,}""").split(trimmed).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> null to trimmed
        }
    }

    private fun parseCityStateZip(text: String?): CityStateZip? {
        if (text.isNullOrBlank()) return null

        val cleaned = text
            .replace("PA17331", "PA 17331")
            .replace(Regex("""\s+"""), " ")
            .trim()

        Regex("""^(Hanover),\s*(PA)\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.let {
                return CityStateZip(
                    city = "Hanover",
                    state = "PA",
                    zip = it.groupValues[3].trim()
                )
            }

        Regex("""^(Hanover)\s+(PA)\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.let {
                return CityStateZip(
                    city = "Hanover",
                    state = "PA",
                    zip = it.groupValues[3].trim()
                )
            }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (raw in lines) {
            val line = normalize(raw)
            if (line.isBlank()) continue
            if (line.startsWith("TOTAL", ignoreCase = true)) continue
            if (line.startsWith("Item Description", ignoreCase = true)) continue
            if (line.equals("Price", ignoreCase = true)) continue
            if (line.equals("Unit", ignoreCase = true)) continue

            val match = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+(\d+(?:,\d{3})?)\s+(EA|PK)\s+([A-Z0-9.]+)\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val rawSku = match.groupValues[1].trim().uppercase()
            val sku = normalizeAquaSku(rawSku)
            val descriptionRaw = match.groupValues[2].trim()
            val quantity = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: continue
            val priceToken = match.groupValues[5].trim()
            val extAmount = match.groupValues[6].replace(",", "").toDoubleOrNull() ?: continue

            val parsedUnitPrice = priceToken.toDoubleOrNull()
            val unitPrice = parsedUnitPrice ?: roundToCents(extAmount / quantity)

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        descriptionRaw.ifBlank { sku }
                    },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun normalizeAquaSku(raw: String): String {
        return when (raw.trim().uppercase()) {
            "PH-0114-1B-50" -> "PH0114-1B-50"
            else -> raw.trim().uppercase()
        }
    }

    private fun roundToCents(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private fun normalize(value: String): String {
        return value
            .replace("[=]", " ")
            .replace("—", " ")
            .replace("–", " ")
            .replace("“", " ")
            .replace("”", " ")
            .replace("|", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun compact(value: String): String {
        return normalize(value)
            .uppercase()
            .replace(Regex("""[^A-Z0-9#]"""), "")
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
        val city: String,
        val state: String,
        val zip: String
    )
}