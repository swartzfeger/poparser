package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SanitechLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "SANITECH"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("SANITECH SYSTEMS") &&
                text.contains("PURCHASE ORDER") &&
                text.contains("DELIVERY DETAILS") &&
                text.contains("DELIVERY ADDRESS")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("SANITECH SYSTEMS")) score += 120
        if (text.contains("PURCHASE ORDER")) score += 80
        if (text.contains("DELIVERY DETAILS")) score += 60
        if (text.contains("DELIVERY ADDRESS")) score += 60
        if (text.contains("QAC-400-1B-500")) score += 80
        if (text.contains("TOTAL USD")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseDeliveryAddress(clean)

        return ParsedPdfFields(
            customerName = "SANITECH",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Sanitech Systems",
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]

            if (line.equals("Purchase Order", ignoreCase = true) &&
                lines.getOrNull(i + 1)?.equals("Number", ignoreCase = true) == true
            ) {
                val next = lines.getOrNull(i + 2)?.trim().orEmpty()
                Regex("""^(PO-\d+)$""", RegexOption.IGNORE_CASE)
                    .find(next)
                    ?.groupValues
                    ?.get(1)
                    ?.let { return it.uppercase() }
            }

            Regex("""\b(PO-\d+)\b""", RegexOption.IGNORE_CASE)
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.let { return it.uppercase() }
        }

        return null
    }

    private fun parseDeliveryAddress(lines: List<String>): ShipToBlock {
        val deliveryIndex = lines.indexOfFirst { it.contains("Delivery Address", ignoreCase = true) }
        if (deliveryIndex < 0) {
            return ShipToBlock(null, null, null, null)
        }

        val window = lines
            .drop(deliveryIndex + 1)
            .take(10)
            .map { it.replace(Regex("""\s+"""), " ").trim() }

        var addressLine1: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        for (line in window) {
            val normalized = line.trim().trimEnd(',')

            if (addressLine1 == null) {
                val street = extractStreetAddress(normalized)
                if (street != null) {
                    addressLine1 = street
                    continue
                }
            }

            val cityMatch = Regex(
                """^(.+?),\s*([A-Z]{2}),\s*(\d{5})$""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (cityMatch != null) {
                city = cityMatch.groupValues[1].trim()
                state = cityMatch.groupValues[2].uppercase()
                zip = cityMatch.groupValues[3]
            }
        }

        if (city == null) {
            val stateIndex = window.indexOfFirst { STATE_ONLY_PATTERN.matches(it.trim()) }
            if (stateIndex >= 0) {
                state = window[stateIndex].trim().uppercase()
                city = window
                    .take(stateIndex)
                    .asReversed()
                    .map { it.trim().trimEnd(',') }
                    .firstOrNull(::looksLikeCity)
                zip = window
                    .drop(stateIndex + 1)
                    .firstNotNullOfOrNull { ZIP_ONLY_PATTERN.matchEntire(it.trim())?.value }
            }
        }

        return ShipToBlock(addressLine1, city, state, zip)
    }

    private fun extractStreetAddress(line: String): String? {
        val withoutInstructions = line
            .replace(DELIVERY_INSTRUCTIONS_PATTERN, "")
            .trim()
            .trimEnd(',')

        STREET_ADDRESS_PATTERN.find(withoutInstructions)?.let { match ->
            return match.groupValues[1].trim().trimEnd(',')
        }

        return withoutInstructions.takeIf { candidate ->
            candidate.isNotBlank() &&
                    candidate.any(Char::isDigit) &&
                    !candidate.equals("USA", ignoreCase = true) &&
                    !candidate.startsWith("Telephone", ignoreCase = true) &&
                    !CITY_STATE_ZIP_PATTERN.matches(candidate)
        }
    }

    private fun looksLikeCity(value: String): Boolean {
        if (!CITY_ONLY_PATTERN.matches(value)) return false

        return !value.equals("Telephone", ignoreCase = true) &&
                !value.equals("USA", ignoreCase = true) &&
                !value.equals("United States", ignoreCase = true)
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val cleanLine = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(cleanLine) ?: continue

            val sku = match.groupValues[1].uppercase()
            val rawDescription = match.groupValues[2].trim()
            val quantity = match.groupValues[3].replace(",", "").toDoubleOrNull()
            val unitPrice = match.groupValues[4].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                cleanupDescription(rawDescription)
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

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("SANITECHSYSTEMS", "SANITECH SYSTEMS")
            .replace("PURCHASEORDER", "PURCHASE ORDER")
            .replace("DELIVERYDETAILS", "DELIVERY DETAILS")
            .replace("DELIVERYADDRESS", "DELIVERY ADDRESS")
            .replace("QAC-400-1B-500", "QAC-400-1B-500")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private data class ShipToBlock(
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private companion object {
        val DELIVERY_INSTRUCTIONS_PATTERN = Regex("""\s+(?:UPS|FEDEX)\b.*$""", RegexOption.IGNORE_CASE)
        val STREET_ADDRESS_PATTERN = Regex(
            """^(\d+\s+.+?\s+(?:ST(?:REET)?|RD|ROAD|DR(?:IVE)?|AVE(?:NUE)?|BLVD|BOULEVARD|LN|LANE|CT|COURT|CIR|CIRCLE|HWY|HIGHWAY|PKWY|PARKWAY|PL|PLACE|TER|TERRACE|WAY)\b\.?)""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^.+?,\s*[A-Z]{2},\s*\d{5}(?:-\d{4})?$""",
            RegexOption.IGNORE_CASE
        )
        val STATE_ONLY_PATTERN = Regex("""^[A-Z]{2}$""", RegexOption.IGNORE_CASE)
        val ZIP_ONLY_PATTERN = Regex("""^\d{5}(?:-\d{4})?$""")
        val CITY_ONLY_PATTERN = Regex("""^[A-Z][A-Z .'-]*$""", RegexOption.IGNORE_CASE)
    }
}
