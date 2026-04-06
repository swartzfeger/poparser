package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AllPointsIlLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "AllPoints IL"

    override fun matches(lines: List<String>): Boolean {
        val normalizedLines = normalizeLines(lines)
        val raw = normalizedLines.joinToString("\n").uppercase()

        return raw.contains("ALLPOINTS") &&
                raw.contains("PURCHASE ORDER") &&
                raw.contains("SHIP TO: ALLPOINTS - MESA")
    }

    override fun score(lines: List<String>): Int {
        val normalizedLines = normalizeLines(lines)
        val raw = normalizedLines.joinToString("\n").uppercase()

        var score = 0
        if (raw.contains("ALLPOINTS")) score += 500
        if (raw.contains("ALLPOINTS PURCHASE ORDER")) score += 500
        if (raw.contains("SHIP TO: ALLPOINTS - MESA")) score += 700
        if (raw.contains("8955 E WARNER RD")) score += 250
        if (raw.contains("SUITE 101")) score += 150
        if (raw.contains("MESA, AZ 85212")) score += 250
        if (Regex("""\b\d{7}-\d{2}\b""").containsMatchIn(raw)) score += 100

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = normalizeLines(nonBlankLines(lines))
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "ALLPOINTS IL",
            orderNumber = parseOrderNumber(clean),
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

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""PURCHASE\s+ORDER\s+NO\.?\s*[:#]?\s*(\d{7}-\d{2})""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues?.get(1)
            ?.let { return it }

        Regex("""\b(\d{7}-\d{2})\b""")
            .find(joined)
            ?.groupValues?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        for (line in lines) {
            if (line.uppercase().contains("PREPAID")) return "PREPAID"

            Regex("""\b(NET\s+\d+|COD)\b""", RegexOption.IGNORE_CASE)
                .find(line)
                ?.value
                ?.trim()
                ?.let { return it }
        }
        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        for (i in lines.indices) {
            val line = lines[i].uppercase()

            if (!line.contains("SHIP TO: ALLPOINTS - MESA")) continue

            val rawLine1 = lines.getOrNull(i + 1)?.trim()
            val rawLine2 = lines.getOrNull(i + 2)?.trim()
            val rawLine3 = lines.getOrNull(i + 3)?.trim()

            // The PDF merges left-side vendor block with right-side ship-to block:
            // "PRECISION LABS 8955 E WARNER RD"
            // "415 AIRPARK ROAD SUITE 101"
            // "COTTONWOOD, AZ 86326 MESA, AZ 85212"
            val addressLine1 = rawLine1
                ?.substringAfter("PRECISION LABS", rawLine1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val addressLine2 = rawLine2
                ?.substringAfter("415 AIRPARK ROAD", rawLine2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val cityLine = rawLine3
                ?.substringAfter("COTTONWOOD, AZ 86326", rawLine3)
                ?.trim()

            val parsedCity = cityLine?.let { parseCityStateZip(it) }

            return ShipToBlock(
                shipToCustomer = "ALLPOINTS - MESA",
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = parsedCity?.city,
                state = parsedCity?.state,
                zip = parsedCity?.zip
            )
        }

        return ShipToBlock(null, null, null, null, null, null)
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in 0 until lines.size - 1) {
            val current = lines[i].replace(Regex("""\s+"""), " ").trim()
            val next = lines[i + 1].replace(Regex("""\s+"""), " ").trim()

            val parsedMain = parseMainItemLine(current) ?: continue
            val sku = parseSkuFromDescriptionLine(next) ?: continue

            val description = ItemMapper.getItemDescription(sku).takeIf { it.isNotBlank() }
                ?: extractDescriptionFromDescriptionLine(next, sku).ifBlank { null }

            val key = "$sku|${parsedMain.qty}|${parsedMain.unitPrice}"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = parsedMain.qty,
                    unitPrice = parsedMain.unitPrice
                )
            )
        }

        return items
    }

    private fun parseMainItemLine(line: String): ParsedMainItem? {
        val match = Regex(
            """^\s*([\d,]+)\s+\S+\s+PKG OF\s+\d+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+DFS\s+([\d,]+\.\d{2,4})\s+([\d,]+\.\d{2})\s+(\d{2}/\d{2}/\d{2})\s*$""",
            RegexOption.IGNORE_CASE
        ).find(line) ?: return null

        val qty = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val unitPrice = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: return null

        return ParsedMainItem(
            qty = qty,
            unitPrice = unitPrice
        )
    }

    private fun parseSkuFromDescriptionLine(line: String): String? {
        val match = Regex(
            """\|\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+DFS\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(line)

        return match?.groupValues?.get(1)?.trim()?.uppercase()
    }

    private fun extractDescriptionFromDescriptionLine(line: String, sku: String): String {
        val match = Regex(
            """^(.*?)\s*\|\s*${Regex.escape(sku)}\s+DFS\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(line)

        return match?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun parseCityStateZip(line: String): CityStateZip {
        val match = Regex(
            """^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        ).find(line.trim())

        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim(),
                state = match.groupValues[2].trim().uppercase(),
                zip = match.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private fun normalizeLines(lines: List<String>): List<String> {
        return lines.map { normalizeDoubledLine(it) }
    }

    private fun normalizeDoubledLine(line: String): String {
        if (!looksDoubled(line)) {
            return line.replace(Regex("""\s+"""), " ").trim()
        }

        val sb = StringBuilder()
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            var j = i + 1
            while (j < line.length && line[j] == ch) j++

            val runLength = j - i
            val keep = if (ch.isLetterOrDigit()) (runLength + 1) / 2 else 1

            repeat(keep) { sb.append(ch) }
            i = j
        }

        return sb.toString()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun looksDoubled(line: String): Boolean {
        val alphaNumChars = line.filter { it.isLetterOrDigit() }
        if (alphaNumChars.length < 8) return false

        var repeatedCount = 0
        var i = 0
        while (i < alphaNumChars.length) {
            var j = i + 1
            while (j < alphaNumChars.length && alphaNumChars[j] == alphaNumChars[i]) j++
            if (j - i >= 2) repeatedCount += (j - i)
            i = j
        }

        return repeatedCount >= alphaNumChars.length / 2
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

    private data class ParsedMainItem(
        val qty: Double,
        val unitPrice: Double
    )
}