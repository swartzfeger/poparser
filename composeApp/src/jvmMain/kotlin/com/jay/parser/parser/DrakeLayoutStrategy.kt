package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class DrakeLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Drake"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("DRAKE SPECIALTIES") &&
                text.contains("PURCHASE ORDER NO.") &&
                text.contains("SHIP TO:")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("DRAKE SPECIALTIES, LLC")) score += 60
        if (text.contains("PURCHASE ORDER NO.")) score += 20
        if (text.contains("SHIP TO:")) score += 20
        if (text.contains("119 VETERINARIAN RD")) score += 40
        if (text.contains("LAFAYETTE, LA 70507")) score += 40

        return score
    }

    override fun parse(textLines: List<String>): ParsedPdfFields {
        val normalized = textLines
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val shipTo = extractShipTo(normalized)

        return ParsedPdfFields(
            customerName = "DRAKE SPECIALTIES, LLC",
            orderNumber = findOrderNumber(normalized),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(normalized),
            items = findDrakeItems(normalized)
        )
    }

    private fun findOrderNumber(lines: List<String>): String? {
        val poLabelIndex = lines.indexOfFirst {
            it.equals("Purchase Order No.", ignoreCase = true)
        }

        if (poLabelIndex != -1) {
            for (i in (poLabelIndex + 1)..minOf(poLabelIndex + 4, lines.lastIndex)) {
                val line = lines[i].trim()
                val match = Regex("""\b\d{4,}\b""").find(line)
                if (match != null) return match.value
            }
        }

        val shipToIndex = lines.indexOfFirst {
            it.equals("Ship To:", ignoreCase = true) ||
                    it.equals("Ship To", ignoreCase = true)
        }

        if (shipToIndex != -1 && shipToIndex + 1 < lines.size) {
            val line = lines[shipToIndex + 1].trim()
            if (line.matches(Regex("""\d{4,}"""))) {
                return line
            }
        }

        return findFirstMatch(
            lines,
            Regex("""Purchase Order No\.?\s*:?\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(lines: List<String>): String? {
        val contactTermsIndex = lines.indexOfFirst {
            it.equals("Contact Terms", ignoreCase = true)
        }

        if (contactTermsIndex != -1 && contactTermsIndex + 1 < lines.size) {
            return lines[contactTermsIndex + 1].trim()
        }

        return findFirstMatch(
            lines,
            Regex("""Contact Terms\s*:?\s*(.+)$""", RegexOption.IGNORE_CASE)
        )
    }

    private fun extractShipTo(lines: List<String>): ShipToBlock {
        val shipToIndex = lines.indexOfFirst {
            it.equals("Ship To:", ignoreCase = true) ||
                    it.equals("Ship To", ignoreCase = true)
        }

        if (shipToIndex == -1) {
            return ShipToBlock(null, null, null, null, null, null)
        }

        val searchEnd = findSearchEnd(lines, shipToIndex)

        val region = lines
            .subList(shipToIndex + 1, searchEnd)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isNoiseLine(it) }
            .filterNot { isBlockedVendorValue(it) }
            .filterNot { it.matches(Regex("""^\d{4,}$""")) }

        val cityIndex = region.indexOfFirst { looksLikeCityStateZip(it) }

        if (cityIndex == -1) {
            return ShipToBlock(null, null, null, null, null, null)
        }

        val parsedCity = parseCityStateZip(region[cityIndex])

        val possibleAddress2 = region.getOrNull(cityIndex - 1)
        val possibleAddress1 = region.getOrNull(cityIndex - 2)
        val possibleName = region.getOrNull(cityIndex - 3)

        return when {
            possibleAddress1 != null && looksLikeStreetAddress(possibleAddress1) -> {
                val address2 =
                    possibleAddress2?.takeIf {
                        !looksLikeStreetAddress(it) &&
                                !looksLikeCityStateZip(it) &&
                                !it.equals("USA", ignoreCase = true)
                    }

                val name =
                    if (!possibleName.isNullOrBlank() && !looksLikeStreetAddress(possibleName)) {
                        possibleName
                    } else {
                        findClosestNameAbove(region, cityIndex - 2)
                    }

                ShipToBlock(
                    shipToCustomer = name,
                    addressLine1 = possibleAddress1,
                    addressLine2 = address2,
                    city = parsedCity.city,
                    state = parsedCity.state,
                    zip = parsedCity.zip
                )
            }

            possibleAddress2 != null && looksLikeStreetAddress(possibleAddress2) -> {
                val name = findClosestNameAbove(region, cityIndex - 1)

                ShipToBlock(
                    shipToCustomer = name,
                    addressLine1 = possibleAddress2,
                    addressLine2 = null,
                    city = parsedCity.city,
                    state = parsedCity.state,
                    zip = parsedCity.zip
                )
            }

            else -> {
                ShipToBlock(
                    shipToCustomer = findClosestNameAbove(region, cityIndex),
                    addressLine1 = null,
                    addressLine2 = null,
                    city = parsedCity.city,
                    state = parsedCity.state,
                    zip = parsedCity.zip
                )
            }
        }
    }

    private fun findSearchEnd(lines: List<String>, shipToIndex: Int): Int {
        val hardStop = lines.indexOfFirst { line ->
            val u = line.uppercase()
            (u.contains("QTY") && u.contains("ITEM") && u.contains("DESCRIPTION")) ||
                    u == "AUTHORIZED SIGNATURE"
        }

        val fallback = minOf(lines.size, shipToIndex + 25)

        return when {
            hardStop != -1 && hardStop > shipToIndex -> hardStop
            else -> fallback
        }
    }

    private fun findClosestNameAbove(region: List<String>, startIndex: Int): String? {
        if (startIndex < 0) return null

        for (i in startIndex downTo maxOf(0, startIndex - 4)) {
            val line = region.getOrNull(i) ?: continue
            if (line.isBlank()) continue
            if (looksLikeStreetAddress(line)) continue
            if (looksLikeCityStateZip(line)) continue
            if (line.equals("USA", ignoreCase = true)) continue
            if (line.matches(Regex("""^\d{4,}$"""))) continue
            return line
        }

        return null
    }

    private fun findDrakeItems(lines: List<String>) =
        buildList {
            val headerIndex = lines.indexOfFirst {
                val u = it.uppercase()
                u.contains("QTY") &&
                        u.contains("ITEM") &&
                        u.contains("DESCRIPTION") &&
                        u.contains("COST") &&
                        u.contains("EXTENSION")
            }

            if (headerIndex == -1) return@buildList

            val itemLines = mutableListOf<String>()

            for (i in (headerIndex + 1) until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                val upper = line.uppercase()

                if (upper.startsWith("TOTAL")) break
                if (upper.contains("AUTHORIZED SIGNATURE")) break
                if (upper == "SHIP VIA") break
                if (upper == "CONTACT TERMS") break
                if (upper == "DATE ISSUED") break
                if (upper == "VALID THRU") break
                if (upper == "SHIP BY") break

                itemLines += line
            }

            if (itemLines.isEmpty()) return@buildList

            val joined = itemLines
                .joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val sku = Regex("""\b\d{3}-\d{3}-[A-Za-z0-9xX-]+\b""")
                .find(joined)
                ?.value
                ?.let { normalizeSku(it) }

            val quantity = Regex("""^\s*([\d,]+(?:\.\d+)?)""")
                .find(joined)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()

            val trailingMoney = Regex("""(\d+\.\d{2})\s+(\d+\.\d{2})\s*$""")
                .find(joined)

            val unitPrice = trailingMoney
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()

            var description = joined

            Regex("""^\s*[\d,]+(?:\.\d+)?""").find(description)?.let {
                description = description.removeRange(it.range).trim()
            }

            trailingMoney?.let {
                description = description.removeSuffix(it.value).trim()
            }

            if (sku != null) {
                description = description.replace(sku, "").trim()
            }

            description = description
                .replace(Regex("""\beach\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

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

    private fun looksLikeStreetAddress(line: String): Boolean {
        val trimmed = line.trim()

        if (!trimmed.any { it.isDigit() }) return false

        val upper = trimmed.uppercase()

        return upper.contains(" RD") ||
                upper.contains(" ROAD") ||
                upper.contains(" ST") ||
                upper.contains(" STREET") ||
                upper.contains(" AVE") ||
                upper.contains(" AVENUE") ||
                upper.contains(" DR") ||
                upper.contains(" DRIVE") ||
                upper.contains(" LN") ||
                upper.contains(" LANE") ||
                upper.contains(" BLVD") ||
                upper.contains(" WAY") ||
                upper.contains(" HWY") ||
                upper.contains(" HIGHWAY") ||
                upper.contains(" PKWY") ||
                upper.contains(" PARKWAY")
    }

    private fun looksLikeCityStateZip(line: String): Boolean {
        return Regex("""^[A-Za-z .'\-]+,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?$""")
            .containsMatchIn(line.trim())
    }

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val match = Regex("""^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""")
            .find(line.trim())

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

    private fun isNoiseLine(line: String): Boolean {
        val u = line.uppercase()

        return u == "SHIP TO:" ||
                u == "PURCHASE ORDER NO." ||
                u == "DATE ISSUED" ||
                u == "VALID THRU" ||
                u == "SHIP BY" ||
                u == "CONTACT TERMS" ||
                u == "SHIP VIA" ||
                u == "ORDERED BY" ||
                u == "RELEASED" ||
                u == "BILL TO" ||
                u == "USA"
    }

    private fun isBlockedVendorValue(line: String): Boolean {
        val u = line.uppercase()

        return u.contains("PRECISION") ||
                u.contains("AIRPORT") ||
                u.contains("COTTONWOOD") ||
                u.contains("86326")
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