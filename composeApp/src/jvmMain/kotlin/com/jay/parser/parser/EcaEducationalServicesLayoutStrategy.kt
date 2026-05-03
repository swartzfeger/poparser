package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class EcaEducationalServicesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "ECA EDUCATIONAL SERVICES"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("ECASCIENCEKITSERVICES") ||
                text.contains("ECAEDUCATIONALSERVICES") ||
                text.contains("STAKACS@ECAKITSERVICES.COM") ||
                text.contains("1981DALLAVODRIVE")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("ECASCIENCEKITSERVICES")) score += 120
        if (text.contains("ECAEDUCATIONALSERVICES")) score += 80
        if (text.contains("STAKACS@ECAKITSERVICES.COM")) score += 70
        if (text.contains("1981DALLAVODRIVE")) score += 70
        if (text.contains("COMMERCETOWNSHIP")) score += 60
        if (text.contains("PONUMBER:K")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "ECA EDUCATIONAL SERV",
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
        val joined = lines.joinToString(" ")
        return Regex("""PO\s+NUMBER:\s*K\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { "K$it" }
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("TERMS NET 30", ignoreCase = true) -> "Net 30"
            joined.contains("NET 30", ignoreCase = true) -> "Net 30"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { it.contains("BILL TO:", ignoreCase = true) && it.contains("SHIP TO:", ignoreCase = true) }
        if (idx >= 0) {
            val nameLine = lines.getOrNull(idx + 1).orEmpty()
            val addressLine = lines.getOrNull(idx + 2).orEmpty()
            val cityLine = lines.getOrNull(idx + 3).orEmpty()

            val shipToCustomer = splitBillToShipToLine(nameLine).second ?: "ECA SCIENCE KIT SERVICES"
            val address1 = splitBillToShipToLine(addressLine).second ?: "1981 DALLAVO DRIVE"
            val cityStateZip = splitBillToShipToLine(cityLine).second ?: "COMMERCE TOWNSHIP MI 48390"
            val parsed = parseCityStateZip(cityStateZip)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = address1,
                city = parsed?.city,
                state = parsed?.state,
                zip = parsed?.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = "ECA SCIENCE KIT SERVICES",
            addressLine1 = "1981 DALLAVO DRIVE",
            city = "COMMERCE TOWNSHIP",
            state = "MI",
            zip = "48390"
        )
    }

    private fun splitBillToShipToLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val billToPrefixes = listOf(
            "ECA Science Kit Services",
            "1981 Dallavo Drive",
            "Commerce Township, Michigan 48390"
        )

        for (prefix in billToPrefixes) {
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
            .replace("COMMERCE TOWNSHIP MI", "COMMERCE TOWNSHIP MI ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = Regex("""^(.+?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim().uppercase(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val match = Regex(
                """^(\d+)\s+([A-Z0-9-]+)\s+([A-Z0-9]+)\s+(.+?)\s+EA\s+([\d.]+)\s+EACH\s+([\d.]+)\s+([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(line.trim()) ?: continue

            val vendorItem = match.groupValues[2].trim()
            val sku = normalizeEcaSku(match.groupValues[3])
            val descriptionFromPo = match.groupValues[4].trim()
            val quantity = match.groupValues[5].toDoubleOrNull() ?: continue
            val unitPrice = match.groupValues[6].toDoubleOrNull() ?: continue

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                descriptionFromPo
            }

            val key = "$vendorItem|$sku|$quantity|$unitPrice"
            if (seen.add(key)) {
                items.add(
                    item(
                        sku = sku,
                        description = description,
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

        return items
    }

    private fun normalizeEcaSku(raw: String): String {
        return when (raw.trim().uppercase()) {
            "CHL3001V100" -> "CHL-300-1V-100"
            else -> raw.trim().uppercase()
        }
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
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