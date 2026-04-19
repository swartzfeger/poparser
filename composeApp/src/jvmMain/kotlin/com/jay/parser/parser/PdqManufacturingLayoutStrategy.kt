package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class PdqManufacturingLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "PDQ MANUFACTURING"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))

        return text.contains("P.D.Q. MANUFACTURING") ||
                (
                        text.contains("PRECISION LABORATORIES") &&
                                text.contains("PLEASE CONFIRM DELIVERY DATE & TIME")
                        ) ||
                text.contains("PURCHASING@PDQONLINE.COM")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("P.D.Q. MANUFACTURING")) score += 120
        if (text.contains("201 VICTORY CIRCLE")) score += 100
        if (text.contains("ELLIJAY, GEORGIA 30540")) score += 80
        if (text.contains("PLEASE CONFIRM DELIVERY DATE & TIME")) score += 60
        if (text.contains("PURCHASING@PDQONLINE.COM")) score += 60
        if (text.contains("PRECISION LABORATORIES")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "PDQ MANUFACTURING, INC.",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""P[O0]\s+NUMBER\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        Regex("""NET\s+30\s+DAYS""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.let { return it }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val normalized = lines.map { it.replace(Regex("""\s+"""), " ").trim() }

        // Future-safe path:
        // if a real SHIP TO block ever appears on a different PDQ layout, use it first.
        val explicitShipToIndex = normalized.indexOfFirst {
            it.equals("SHIP TO", ignoreCase = true) ||
                    it.startsWith("SHIP TO:", ignoreCase = true) ||
                    it.contains("SHIP TO ADDRESS", ignoreCase = true)
        }

        if (explicitShipToIndex >= 0) {
            val window = normalized.drop(explicitShipToIndex + 1).take(6)

            var shipToCustomer: String? = null
            var addressLine1: String? = null
            var city: String? = null
            var state: String? = null
            var zip: String? = null

            for (line in window) {
                if (shipToCustomer == null &&
                    looksLikeCompanyLine(line) &&
                    !line.contains("PRECISION LABORATORIES", ignoreCase = true)
                ) {
                    shipToCustomer = line
                    continue
                }

                if (addressLine1 == null && looksLikeStreet(line)) {
                    addressLine1 = line
                    continue
                }

                val cityMatch = Regex(
                    """^(.+?),\s*(?:[A-Z][a-z]+|[A-Z]{2,})\s+(\d{5})$"""
                ).find(line)

                if (cityMatch != null) {
                    val parts = line.split(",")
                    city = parts.firstOrNull()?.trim()

                    val stateZip = parts.getOrNull(1)?.trim().orEmpty()
                    Regex("""([A-Z]{2})\s+(\d{5})""")
                        .find(stateZip.uppercase())
                        ?.let {
                            state = it.groupValues[1]
                            zip = it.groupValues[2]
                        }

                    if (state == null || zip == null) {
                        Regex("""([A-Z][a-z]+)\s+(\d{5})""")
                            .find(stateZip)
                            ?.let {
                                state = stateFromName(it.groupValues[1])
                                zip = it.groupValues[2]
                            }
                    }
                }
            }

            if (shipToCustomer != null || addressLine1 != null || city != null) {
                return ShipToBlock(
                    shipToCustomer = shipToCustomer,
                    addressLine1 = addressLine1,
                    city = city,
                    state = state,
                    zip = zip
                )
            }
        }

        // Current known PDQ format:
        // top-left header is the actual customer ship-to block.
        val company = normalized.getOrNull(1)
        val address = normalized.getOrNull(2)
        val cityStateZipLine = normalized.getOrNull(3)

        var city: String? = null
        var state: String? = null
        var zip: String? = null

        Regex("""^(.+?),\s*([A-Za-z]+)\s+(\d{5})$""")
            .find(cityStateZipLine.orEmpty())
            ?.let {
                city = it.groupValues[1].trim()
                state = stateFromName(it.groupValues[2].trim())
                zip = it.groupValues[3].trim()
            }

        return ShipToBlock(
            shipToCustomer = company,
            addressLine1 = address,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()

            val match = Regex(
                """^(\d+)\s*/\s*(.+?)\s+PER\s+BAG\s*-\s*([A-Z0-9-]+)\s+(\d+)\s+(\d+\.\d{4})\s+(\d+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val rawDescription = match.groupValues[2].trim()
            val sku = match.groupValues[3].trim().uppercase()
            val quantity = match.groupValues[4].toDoubleOrNull()
            val unitPrice = match.groupValues[5].toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                rawDescription
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

    private fun looksLikeCompanyLine(line: String): Boolean {
        val value = line.trim()
        if (value.isBlank()) return false
        if (looksLikeStreet(value)) return false
        if (Regex(""".+,\s*[A-Za-z]+\s+\d{5}$""").matches(value)) return false
        if (value.startsWith("TEL", true) || value.startsWith("FAX", true)) return false
        if (value.contains("@")) return false
        return value.any { it.isLetter() }
    }

    private fun looksLikeStreet(line: String): Boolean {
        val value = line.trim()
        return value.any { it.isDigit() } &&
                (
                        value.contains("ST", true) ||
                                value.contains("STREET", true) ||
                                value.contains("RD", true) ||
                                value.contains("ROAD", true) ||
                                value.contains("AVE", true) ||
                                value.contains("AVENUE", true) ||
                                value.contains("CIRCLE", true) ||
                                value.contains("DR", true) ||
                                value.contains("DRIVE", true) ||
                                value.contains("LN", true) ||
                                value.contains("LANE", true) ||
                                value.contains("BLVD", true)
                        )
    }

    private fun stateFromName(value: String): String {
        return when (value.trim().uppercase()) {
            "ALABAMA" -> "AL"
            "ALASKA" -> "AK"
            "ARIZONA" -> "AZ"
            "ARKANSAS" -> "AR"
            "CALIFORNIA" -> "CA"
            "COLORADO" -> "CO"
            "CONNECTICUT" -> "CT"
            "DELAWARE" -> "DE"
            "FLORIDA" -> "FL"
            "GEORGIA" -> "GA"
            "HAWAII" -> "HI"
            "IDAHO" -> "ID"
            "ILLINOIS" -> "IL"
            "INDIANA" -> "IN"
            "IOWA" -> "IA"
            "KANSAS" -> "KS"
            "KENTUCKY" -> "KY"
            "LOUISIANA" -> "LA"
            "MAINE" -> "ME"
            "MARYLAND" -> "MD"
            "MASSACHUSETTS" -> "MA"
            "MICHIGAN" -> "MI"
            "MINNESOTA" -> "MN"
            "MISSISSIPPI" -> "MS"
            "MISSOURI" -> "MO"
            "MONTANA" -> "MT"
            "NEBRASKA" -> "NE"
            "NEVADA" -> "NV"
            "NEW HAMPSHIRE" -> "NH"
            "NEW JERSEY" -> "NJ"
            "NEW MEXICO" -> "NM"
            "NEW YORK" -> "NY"
            "NORTH CAROLINA" -> "NC"
            "NORTH DAKOTA" -> "ND"
            "OHIO" -> "OH"
            "OKLAHOMA" -> "OK"
            "OREGON" -> "OR"
            "PENNSYLVANIA" -> "PA"
            "RHODE ISLAND" -> "RI"
            "SOUTH CAROLINA" -> "SC"
            "SOUTH DAKOTA" -> "SD"
            "TENNESSEE" -> "TN"
            "TEXAS" -> "TX"
            "UTAH" -> "UT"
            "VERMONT" -> "VT"
            "VIRGINIA" -> "VA"
            "WASHINGTON" -> "WA"
            "WEST VIRGINIA" -> "WV"
            "WISCONSIN" -> "WI"
            "WYOMING" -> "WY"
            else -> value.trim().uppercase()
        }
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}