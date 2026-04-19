package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class VikingPureLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "VIKING PURE"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("VIKING PURE SOLUTIONS") ||
                text.contains("INFO@VIKINGPURE.COM") ||
                (text.contains("ORDER#") && text.contains("SHIP TO:") && text.contains("VENDOR:"))
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("VIKING PURE SOLUTIONS")) score += 120
        if (text.contains("INFO@VIKINGPURE.COM")) score += 80
        if (text.contains("4400 EASTPORT PARK WAY")) score += 60
        if (text.contains("PORT ORANGE, FL 32127")) score += 60
        if (text.contains("PREPAID & BILLED")) score += 40
        if (text.contains("DATE SCHEDULED")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "VIKING PURE SOLUTIONS",
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

        Regex("""\bPPO\d{2}-\d+\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.let { return it }

        Regex("""\bPO\d{2}-\d+\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""\bCOD\b""", RegexOption.IGNORE_CASE).find(joined)?.value?.uppercase()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { normalize(it).contains("VENDOR: SHIP TO:") }
        if (idx >= 0) {
            val line1 = lines.getOrNull(idx + 1).orEmpty()
            val line2 = lines.getOrNull(idx + 2).orEmpty()
            val line3 = lines.getOrNull(idx + 3).orEmpty()

            val shipToCustomer = splitVendorShipToLine(line1).second
            val addressLine1 = splitVendorShipToLine(line2).second
            val cityStateZipText = splitVendorShipToLine(line3).second

            val csz = parseCityStateZip(cityStateZipText)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                city = csz?.city,
                state = csz?.state,
                zip = csz?.zip
            )
        }

        return ShipToBlock(null, null, null, null, null)
    }

    private fun splitVendorShipToLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val knownVendorPrefixes = listOf(
            "PRECISION LABORATORIES, INC.",
            "415 AIORAR PARK ROAD",
            "COTTONWOOD, AZ 86326"
        )

        for (prefix in knownVendorPrefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val right = trimmed.removePrefix(prefix).trim()
                return prefix to right.ifBlank { null }
            }
        }

        val matches = Regex("""[A-Za-z0-9 .,&'-]+,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?""")
            .findAll(trimmed)
            .map { it.value.trim() }
            .toList()

        if (matches.size >= 2) {
            return matches[0] to matches[1]
        }

        val parts = Regex("""\s{2,}""").split(trimmed).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> null to trimmed
        }
    }

    private fun parseCityStateZip(text: String?): CityStateZip? {
        if (text.isNullOrBlank()) return null

        val match = Regex("""^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(text.trim())
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            val match = Regex(
                """^(\d+)\s+Purchase\s+([A-Z0-9-]+)\s+(.+?)\s+\$?\s*([\d.]+)\s+([\d,]+)\s+([A-Za-z]{1,4})\s+\$?\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (match != null) {
                var sku = match.groupValues[2].trim().uppercase()
                val firstDescPart = match.groupValues[3].trim()
                val unitPrice = match.groupValues[4].replace(",", "").toDoubleOrNull()
                val quantity = match.groupValues[5].replace(",", "").toDoubleOrNull()

                if (quantity == null || unitPrice == null) {
                    i++
                    continue
                }

                val descriptionParts = mutableListOf<String>()

                val nextLine = lines.getOrNull(i + 1)?.trim().orEmpty()

                // Repair wrapped SKU:
                // CHL-300-1V-  + 100 -> CHL-300-1V-100
                // PH0114-1V-   + 100 -> PH0114-1V-100
                if (sku.endsWith("-")) {
                    val continuationMatch = Regex("""^(\d+)\s+(.+)$""").find(nextLine)
                    if (continuationMatch != null) {
                        sku += continuationMatch.groupValues[1]
                        val remainder = continuationMatch.groupValues[2].trim()
                        if (remainder.isNotBlank()) {
                            descriptionParts.add(firstDescPart)
                            descriptionParts.add(remainder)
                        } else if (firstDescPart.isNotBlank()) {
                            descriptionParts.add(firstDescPart)
                        }
                        i += 1
                    } else {
                        descriptionParts.add(firstDescPart)
                    }
                } else {
                    descriptionParts.add(firstDescPart)

                    if (nextLine.isNotBlank() &&
                        !looksLikeNewItem(nextLine) &&
                        !looksLikeFooter(nextLine) &&
                        !Regex("""^\d+\s+.+$""").matches(nextLine)
                    ) {
                        descriptionParts.add(nextLine)
                        i += 1
                    }
                }

                val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { "" }
                val description = if (mappedDescription.isNotBlank()) {
                    mappedDescription
                } else {
                    descriptionParts.joinToString(" ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                }

                val key = "$sku|$quantity|$unitPrice"
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

            i++
        }

        return items
    }

    private fun looksLikeNewItem(line: String): Boolean {
        return Regex("""^\d+\s+Purchase\s+""", RegexOption.IGNORE_CASE).containsMatchIn(line)
    }

    private fun looksLikeFooter(line: String): Boolean {
        val text = normalize(line)
        return text.startsWith("TOTAL:") ||
                text.startsWith("APPROVAL:") ||
                text.contains("PAGE1OF") ||
                text.contains("DATE SCHEDULED") ||
                text.startsWith("BUYER PAYMENT TERMS")
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("\uFFFE", "-")
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

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )
}