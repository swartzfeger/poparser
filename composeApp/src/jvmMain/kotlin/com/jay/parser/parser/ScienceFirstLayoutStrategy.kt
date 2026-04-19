package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ScienceFirstLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "SCIENCE FIRST"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("SCIENCE FIRST") ||
                text.contains("SCIENCE INTERACTIVE GROUP") ||
                (text.contains("BUY FROM SHIP TO") && text.contains("PURCHASE ORDER"))
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("SCIENCE FIRST")) score += 120
        if (text.contains("SCIENCE INTERACTIVE GROUP")) score += 100
        if (text.contains("BUY FROM SHIP TO")) score += 80
        if (text.contains("ACCOUNTING@SCIENCEFIRST.COM")) score += 60
        if (text.contains("PURCHASING@SCIENCEFIRST.COM")) score += 60
        if (text.contains("PAYMENT TERMS")) score += 40
        if (text.contains("PAY BY ACH")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "SCIENCE FIRST",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = parseShipToCustomer(clean),
            addressLine1 = parseAddressLine1(clean),
            addressLine2 = null,
            city = parseCity(clean),
            state = parseState(clean),
            zip = parseZip(clean),
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""\bPO-\d+\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""NET\s*30\s*DAYS""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.replace(Regex("""\s+"""), " ")
            ?.replace("NET30DAYS", "Net 30 Days")
            ?: if (joined.contains("Net30Days", ignoreCase = true)) "Net 30 Days" else null
    }

    private fun parseShipToCustomer(lines: List<String>): String? {
        val idx = lines.indexOfFirst { normalize(it) == "BUY FROM SHIP TO" }
        if (idx >= 0) {
            val line = lines.getOrNull(idx + 1)?.trim()
            if (!line.isNullOrBlank()) {
                val split = splitCombinedBuyFromShipToLine(line)
                return split.second
            }
        }

        return null
    }

    private fun parseAddressLine1(lines: List<String>): String? {
        val idx = lines.indexOfFirst { normalize(it) == "BUY FROM SHIP TO" }
        if (idx >= 0) {
            val line = lines.getOrNull(idx + 2)?.trim()
            if (!line.isNullOrBlank()) {
                val split = splitCombinedBuyFromShipToLine(line)
                return split.second
            }
        }

        return null
    }

    private fun parseCity(lines: List<String>): String? {
        val cityStateZip = parseCityStateZipLine(lines) ?: return null
        return cityStateZip.city
    }

    private fun parseState(lines: List<String>): String? {
        val cityStateZip = parseCityStateZipLine(lines) ?: return null
        return cityStateZip.state
    }

    private fun parseZip(lines: List<String>): String? {
        val cityStateZip = parseCityStateZipLine(lines) ?: return null
        return cityStateZip.zip
    }

    private fun parseCityStateZipLine(lines: List<String>): CityStateZip? {
        val idx = lines.indexOfFirst { normalize(it) == "BUY FROM SHIP TO" }
        if (idx < 0) return null

        val line = lines.getOrNull(idx + 3)?.trim().orEmpty()
        if (line.isBlank()) return null

        val split = splitCombinedCityStateZipLine(line)
        val shipToPart = split.second ?: return null

        val match = Regex("""^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(shipToPart)
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

        for (i in lines.indices) {
            val line = lines[i].trim()

            val match = Regex(
                """^([A-Z0-9-]+)\s+([A-Z0-9-]+)\s+([\d,]+)\s+([A-Z]{1,4})\s+\d{1,2}/\d{1,2}/\d{4}\s+\$([\d.]+)\s+\$([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val sku = match.groupValues[1].trim().uppercase()
            val vendorItem = match.groupValues[2].trim().uppercase()
            val quantity = match.groupValues[3].replace(",", "").toDoubleOrNull()
            val unitPrice = match.groupValues[5].toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val description = buildDescription(lines, i, sku, vendorItem)

            val key = "$sku|$vendorItem|$quantity|$unitPrice"
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

    private fun buildDescription(lines: List<String>, itemLineIndex: Int, sku: String, vendorItem: String): String {
        val mapped = ItemMapper.getItemDescription(sku).ifBlank { "" }
        if (mapped.isNotBlank()) return mapped

        val vendorMapped = ItemMapper.getItemDescription(vendorItem).ifBlank { "" }
        if (vendorMapped.isNotBlank()) return vendorMapped

        val prev1 = lines.getOrNull(itemLineIndex - 1)?.trim().orEmpty()
        val next1 = lines.getOrNull(itemLineIndex + 1)?.trim().orEmpty()

        return listOf(prev1, next1)
            .filter { it.isNotBlank() }
            .filterNot { normalize(it).contains("YOURITEM# OURITEM# ITEMDESCRIPTION") }
            .filterNot { normalize(it).contains("REQUESTED PROMISED") }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun splitCombinedBuyFromShipToLine(line: String): Pair<String?, String?> {
        val parts = Regex("""\s{2,}""").split(line).filter { it.isNotBlank() }
        if (parts.size >= 2) {
            return parts[0].trim() to parts[1].trim()
        }

        val knownBuyFromPrefixes = listOf(
            "Precision Laboratories, Inc.",
            "415 Airpark Road",
            "Cottonwood, AZ 86326",
            "US"
        )

        for (prefix in knownBuyFromPrefixes) {
            if (line.startsWith(prefix, ignoreCase = true)) {
                val remainder = line.removePrefix(prefix).trim()
                return prefix to remainder.ifBlank { null }
            }
        }

        return null to line.trim()
    }

    private fun splitCombinedCityStateZipLine(line: String): Pair<String?, String?> {
        val matches = Regex("""[A-Za-z .'-]+,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?""")
            .findAll(line)
            .map { it.value.trim() }
            .toList()

        return when {
            matches.size >= 2 -> matches[0] to matches[1]
            matches.size == 1 -> null to matches[0]
            else -> null to null
        }
    }

    private fun normalize(text: String): String {
        return text.uppercase().replace(Regex("""\s+"""), " ").trim()
    }

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )
}