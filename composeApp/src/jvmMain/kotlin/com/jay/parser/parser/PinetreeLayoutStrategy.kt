package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class PinetreeLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Pinetree"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()
        return text.contains("PINETREE INSTRUMENTS INC.") ||
                text.contains("PINETREE/INDIGO INSTRUMENTS INC.")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("PINETREE INSTRUMENTS INC.")) score += 50
        if (text.contains("PINETREE/INDIGO INSTRUMENTS INC.")) score += 50
        if (text.contains("PURCHASE ORDER #")) score += 20
        if (text.contains("SHIP TO:")) score += 20
        if (text.contains("DROP SHIP TO:")) score += 20
        if (text.contains("WATERLOO, ON")) score += 15
        if (text.contains("CITY OF INDUSTRY, CA")) score += 15
        if (text.contains("CRANBERRY TOWNSHIP, PA")) score += 15

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findPinetreeShipTo(textLines)

        return ParsedPdfFields(
            customerName = findCustomerName(textLines),
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findPinetreeItems(textLines)
        )
    }

    private fun findCustomerName(textLines: List<String>): String? {
        return when {
            textLines.any { it.contains("Pinetree/Indigo Instruments Inc.", ignoreCase = true) } ->
                "PINETREE INSTRUMENTS INC."

            textLines.any { it.contains("Pinetree Instruments Inc.", ignoreCase = true) } ->
                "PINETREE INSTRUMENTS INC."

            else -> null
        }
    }

    private fun findOrderNumber(textLines: List<String>): String? {
        val poIndex = textLines.indexOfFirst {
            it.equals("Purchase Order #", ignoreCase = true)
        }

        if (poIndex != -1) {
            for (i in (poIndex + 1)..minOf(poIndex + 4, textLines.lastIndex)) {
                val line = textLines[i].trim()
                if (line.matches(Regex("""^\d{4,}$"""))) {
                    return line
                }
            }
        }

        return findFirstMatch(
            textLines,
            Regex("""Purchase Order\s*#\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(textLines: List<String>): String? {
        return findFirstMatch(
            textLines,
            Regex("""\b1%\s*10\s*Net\s*30\b""", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Pinetree ship-to block is the block immediately after the Precision/Cottonwood vendor block
     * and before the product table.
     */
    private fun findPinetreeShipTo(textLines: List<String>): ShipToBlock {
        val tableStart = textLines.indexOfFirst {
            it.contains("Product Code", ignoreCase = true) &&
                    it.contains("Product Name", ignoreCase = true)
        }.let { if (it == -1) minOf(textLines.size, 40) else it }

        val topRegion = textLines
            .subList(0, tableStart)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val precisionEnd = topRegion.indexOfLast { isPrecisionVendorBlockLine(it) }
        val start = if (precisionEnd == -1) 0 else precisionEnd + 1

        val block = topRegion
            .subList(start, topRegion.size)
            .filterNot { isTopNoiseLine(it) }

        val cityIndex = block.indexOfFirst { looksLikeCityStateZipOrPostal(it) }
        if (cityIndex == -1) {
            return ShipToBlock(null, null, null, null, null, null)
        }

        val parsedCity = parseCityStateZipOrPostal(block[cityIndex])

        val beforeCity = block.subList(0, cityIndex)
            .filterNot { isCountryOrContactLine(it) }
            .filterNot { isNumericOnly(it) }

        val shipToCustomer = beforeCity.firstOrNull {
            !looksLikeAddressLine(it) && !isParentheticalVendorAlias(it)
        }

        val addressLines = beforeCity.filter {
            looksLikeAddressLine(it)
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLines.getOrNull(0),
            addressLine2 = addressLines.getOrNull(1),
            city = parsedCity.city,
            state = parsedCity.state,
            zip = parsedCity.zip
        )
    }

    private fun findPinetreeItems(lines: List<String>) =
        buildList {
            val headerIndex = lines.indexOfFirst {
                val u = it.uppercase()
                u.contains("PRODUCT CODE") && u.contains("PRODUCT NAME")
            }

            if (headerIndex == -1) return@buildList

            val rawItemLines = mutableListOf<String>()

            for (i in (headerIndex + 1) until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                val upper = line.uppercase()

                if (upper == "NOTES") break
                if (upper.startsWith("NOTES ")) break
                if (upper.startsWith("TOTAL:")) break
                if (upper.startsWith("SUB TOTAL:")) break
                if (upper.startsWith("GST")) break
                if (upper.startsWith("PST")) break
                if (upper.startsWith("PRINTED:")) break

                rawItemLines += line
            }

            if (rawItemLines.isEmpty()) return@buildList

            val rows = mutableListOf<MutableList<String>>()
            var current = mutableListOf<String>()

            for (line in rawItemLines) {
                val startsNewRow =
                    Regex("""^\d{5}-[A-Z0-9]+""").containsMatchIn(line) ||
                            Regex("""^\d{5,}\b""").containsMatchIn(line)

                if (startsNewRow && current.isNotEmpty()) {
                    rows += current
                    current = mutableListOf()
                }

                current += line
            }

            if (current.isNotEmpty()) {
                rows += current
            }

            for (row in rows) {
                val joined = row.joinToString(" ").replace(Regex("""\s+"""), " ").trim()

                val qtyPrice = parseQtyPrice(joined)
                val rawSku = extractPinetreeSku(row)

                val normalizedSku = rawSku
                    ?.replace(Regex("""\s+INDIGO$""", RegexOption.IGNORE_CASE), "")
                    ?.replace(Regex("""\s+"""), "")
                    ?.replace("LOTOF25", "", ignoreCase = true)
                    ?.replace("-EACH", "-")
                    ?.let { normalizeSku(it) }

                if (!normalizedSku.isNullOrBlank()) {
                    var description = joined

                    Regex("""^\d{5}-[A-Z0-9]+\s*""").find(description)?.let {
                        description = description.removeRange(it.range).trim()
                    }

                    if (!rawSku.isNullOrBlank()) {
                        description = description.replace(rawSku, "").trim()
                    }

                    if (!normalizedSku.isNullOrBlank() && normalizedSku != rawSku) {
                        description = description.replace(normalizedSku, "").trim()
                    }

                    qtyPrice.matchedText?.let {
                        description = description.replace(it, "").trim()
                    }

                    description = description
                        .replace(Regex("""\s+"""), " ")
                        .trim()

                    add(
                        item(
                            sku = normalizedSku,
                            description = ItemMapper.getItemDescription(normalizedSku).ifBlank {
                                description.ifBlank { null }
                            },
                            quantity = qtyPrice.quantity,
                            unitPrice = qtyPrice.unitPrice
                        )
                    )
                }
            }
        }

    private fun parseQtyPrice(joined: String): QtyPrice {
        val direct = Regex(
            """(\d+(?:,\d{3})*(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+0\.00%\s+(\d[\d,]*\.\d{2})"""
        ).findAll(joined).lastOrNull()

        if (direct != null) {
            return QtyPrice(
                quantity = direct.groupValues[1].replace(",", "").toDoubleOrNull(),
                unitPrice = direct.groupValues[2].toDoubleOrNull(),
                matchedText = direct.value
            )
        }

        val skuStripped = joined
            .replace(Regex("""[A-Z0-9]+(?:-[A-Z0-9xX]+)+"""), " ")
            .replace(Regex("""\bEACH\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bINDIGO\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val split = Regex(
            """(\d+(?:,\d{3})*(?:\.\d+)?)\s+(\d+\.\d*|\d+)\s+(\d{1,2})\s+0\.00%\s+(\d[\d,]*\.\d{2})"""
        ).findAll(skuStripped).lastOrNull()

        if (split != null) {
            return QtyPrice(
                quantity = split.groupValues[1].replace(",", "").toDoubleOrNull(),
                unitPrice = combineSplitPrice(split.groupValues[2], split.groupValues[3]),
                matchedText = split.value
            )
        }

        return QtyPrice(null, null, null)
    }

    private fun combineSplitPrice(left: String, right: String): Double? {
        val l = left.trim()
        val r = right.trim()

        val combined = if (l.contains(".")) {
            val parts = l.split(".")
            val whole = parts[0]
            val frac = (parts.getOrNull(1).orEmpty() + r).take(2)
            "$whole.$frac"
        } else {
            "$l.$r"
        }

        return combined.toDoubleOrNull()
    }

    private fun extractPinetreeSku(rowLines: List<String>): String? {
        val joined = rowLines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()

        val merged = joined
            .replace(
                Regex(
                    """-\s+EACH\s+([A-Z0-9]+(?:-[A-Z0-9xX]+)*(?:\s+Indigo)?)""",
                    RegexOption.IGNORE_CASE
                ),
                "-$1"
            )
            .replace(Regex("""-\s+LOTOF\d+""", RegexOption.IGNORE_CASE), "")

        val withIndigo = Regex(
            """\b([A-Z0-9]+(?:-[A-Z0-9xX]+)+\s+Indigo)\b""",
            RegexOption.IGNORE_CASE
        ).find(merged)?.groupValues?.getOrNull(1)
        if (withIndigo != null) return withIndigo.trim()

        val candidates = Regex("""\b([A-Z0-9]+(?:-[A-Z0-9xX]+)+)\b""")
            .findAll(merged)
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("PO") }
            .filterNot { it.startsWith("UPS") }
            .filterNot { it.startsWith("STD") }
            .filterNot { Regex("""^\d{5}-[A-Z0-9]+$""").matches(it) }
            .toList()

        return candidates.lastOrNull()?.trim()
    }

    private fun looksLikeAddressLine(line: String): Boolean {
        return looksLikePrimaryStreetAddress(line) || looksLikeAddressContinuation(line)
    }

    private fun looksLikePrimaryStreetAddress(line: String): Boolean {
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
                upper.contains(" COURT") ||
                upper.contains(" CT") ||
                upper.contains(" ROUTE") ||
                upper.contains(" HIGHWAY") ||
                upper.contains(" HWY") ||
                upper.contains(" UNIT")
    }

    private fun looksLikeAddressContinuation(line: String): Boolean {
        val upper = line.trim().uppercase()
        return upper.startsWith("BUILDING ") ||
                upper.startsWith("SUITE ") ||
                upper.startsWith("STE ") ||
                upper.startsWith("UNIT ")
    }

    private fun looksLikeCityStateZipOrPostal(line: String): Boolean {
        val trimmed = line.trim()

        val us = Regex("""^[A-Za-z .'\-]+,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?$""")
        val canada = Regex("""^[A-Za-z .'\-]+,\s*[A-Z]{2}\s+[A-Z]\d[A-Z]\s?\d[A-Z]\d$""", RegexOption.IGNORE_CASE)

        return us.containsMatchIn(trimmed) || canada.containsMatchIn(trimmed)
    }

    private fun parseCityStateZipOrPostal(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val trimmed = line.trim()

        val us = Regex("""^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""").find(trimmed)
        if (us != null) {
            return CityStateZip(
                city = us.groupValues[1].trim(),
                state = us.groupValues[2].trim(),
                zip = us.groupValues[3].trim()
            )
        }

        val canada = Regex("""^(.*?),\s*([A-Z]{2})\s+([A-Z]\d[A-Z]\s?\d[A-Z]\d)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (canada != null) {
            return CityStateZip(
                city = canada.groupValues[1].trim(),
                state = canada.groupValues[2].trim(),
                zip = canada.groupValues[3].trim().uppercase()
            )
        }

        return CityStateZip(null, null, null)
    }

    private fun isTopNoiseLine(line: String): Boolean {
        val u = line.uppercase()

        return u.contains("VENDOR:") ||
                u.contains("SHIP TO:") ||
                u.contains("DROP SHIP TO:") ||
                u.contains("P/O DATE") ||
                u.contains("SHIP DATE") ||
                u.contains("TERMS") ||
                u.contains("SHIP VIA") ||
                u.contains("VENDOR ID") ||
                u.contains("PURCHASE ORDER #") ||
                u.startsWith("DATE:") ||
                u.startsWith("PHONE:") ||
                u.startsWith("FAX:") ||
                u.contains("PINETREE INSTRUMENTS INC.") ||
                Regex("""^\d{1,2}/\d{1,2}/\d{2}$""").matches(line.trim()) ||
                Regex("""^\(\d{3}\)\s*\d{3}-\d{4}$""").matches(line.trim())
    }

    private fun isPrecisionVendorBlockLine(line: String): Boolean {
        val u = line.uppercase()

        return u.contains("PRECISION LABORATORIES") ||
                u.contains("415 AIRPARK RD") ||
                u.contains("COTTONWOOD") ||
                u.contains("86326") ||
                u.contains("FAX: (928)")
    }

    private fun isCountryOrContactLine(line: String): Boolean {
        val u = line.uppercase()
        return u == "US" ||
                u == "USA" ||
                u == "CANADA" ||
                u.startsWith("ATTN:") ||
                u.startsWith("PHONE:")
    }

    private fun isParentheticalVendorAlias(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("(") &&
                trimmed.endsWith(")") &&
                trimmed.contains("Pinetree", ignoreCase = true)
    }

    private fun isNumericOnly(line: String): Boolean {
        return line.trim().matches(Regex("""^\d+$"""))
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

    private data class QtyPrice(
        val quantity: Double?,
        val unitPrice: Double?,
        val matchedText: String?
    )
}