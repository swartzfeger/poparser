package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DiversifiedFoodserviceLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Diversified Foodservice"

    override fun matches(lines: List<String>): Boolean {
        val raw = lines.joinToString("\n").uppercase()
        val normalized = lines.joinToString("\n") { normalizeForMatch(it) }

        val hasDfs =
            raw.contains("DDFFSS") ||
                    normalized.contains("DFS") ||
                    normalized.contains("DIVERSIFIED FOODSERVICE")

        val hasPurchaseOrder =
            raw.contains("PPUURRCCHHAASSEE OORRDDEERR") ||
                    normalized.contains("PURCHASE ORDER")

        val hasShipTo =
            raw.contains("SSHHIIPP TTOO") ||
                    raw.contains("SHIP TO") ||
                    normalized.contains("SHIP TO")

        return hasDfs && hasPurchaseOrder && hasShipTo
    }

    override fun score(lines: List<String>): Int {
        val raw = lines.joinToString("\n").uppercase()
        val normalized = lines.joinToString("\n") { normalizeForMatch(it) }

        var score = 0

        if (raw.contains("DDFFSS") || normalized.contains("DFS")) score += 80
        if (
            raw.contains("DDIIVVEERRSSIIFFIIEEDD FFOOOODDSSEERRVVIICCEE") ||
            normalized.contains("DIVERSIFIED FOODSERVICE") ||
            normalized.contains("FOODSERVICE SUPPLY")
        ) score += 100

        if (raw.contains("PPUURRCCHHAASSEE OORRDDEERR") || normalized.contains("PURCHASE ORDER")) score += 60
        if (raw.contains("SSHHIIPP TTOO") || normalized.contains("SHIP TO")) score += 50
        if (normalized.contains("PREPAID")) score += 25

        if (normalized.contains("8079625-00")) score += 30
        if (normalized.contains("354524-00")) score += 30

        if (normalized.contains("8955 E WARNER RD")) score += 50
        if (normalized.contains("MESA, AZ 85212")) score += 50

        if (normalized.contains("101 MOUNT HOLLY BYPASS")) score += 50
        if (normalized.contains("LUMBERTON, NJ 08048")) score += 50

        if (normalized.contains("145-144V-100 DFS")) score += 40
        if (normalized.contains("PAA-50-1V-100 DFS")) score += 40
        if (normalized.contains("CHL-1000-1V-100 DFS")) score += 40
        if (normalized.contains("PH3060-1B-50")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "DIVERSIFIED FOODSERVICE",
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
        val decodedLines = lines.map { decodeDoubledLine(it) }
        val joinedDecoded = decodedLines.joinToString("\n")
        val joinedRaw = lines.joinToString("\n")

        Regex("""\b(\d{6,8}-\d{2})\b""")
            .find(joinedDecoded)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(\d{6,8}-\d{2})\b""")
            .find(joinedRaw)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(\d{10,20}--\d{4,8})\b""")
            .find(joinedRaw)
            ?.groupValues
            ?.get(1)
            ?.let { doubled ->
                val fixed = undoubleToken(doubled).replace("--", "-")
                if (fixed.matches(Regex("""\d{6,8}-\d{2}"""))) return fixed
            }

        Regex("""PURCHASE\s+ORDER\s+NO\.?\s*[:#]?\s*(\d{6,8}-\d{2})""", RegexOption.IGNORE_CASE)
            .find(joinedDecoded)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        for (line in lines) {
            val normalized = normalizeForMatch(line)

            if (normalized.contains("PREPAID")) return "PREPAID"

            Regex("""\b(NET\s+\d+|COD)\b""", RegexOption.IGNORE_CASE)
                .find(normalized)
                ?.value
                ?.let { return it.trim() }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val decoded = lines.map { decodeDoubledLine(it) }

        for (i in decoded.indices) {
            val line = decoded[i]
            val normalized = normalizeForMatch(line)

            if (!normalized.contains("SHIP TO")) continue

            val shipToCustomer = extractShipToCustomer(line)
                ?: extractShipToCustomer(normalized)
                ?: "SHIP TO"

            val possibleLine1 = decoded.getOrNull(i + 1)?.let {
                extractShipSide(
                    line = it,
                    leftSideMarkers = listOf(
                        "PRECISION LABS",
                        "PRECISION LAB",
                        "Vendor:"
                    )
                )
            }

            val possibleLine2 = decoded.getOrNull(i + 2)?.let {
                extractShipSide(
                    line = it,
                    leftSideMarkers = listOf(
                        "415 AIRPARK ROAD",
                        "AIRPARK ROAD"
                    )
                )
            }

            val possibleLine3 = decoded.getOrNull(i + 3)?.let {
                extractShipSide(
                    line = it,
                    leftSideMarkers = listOf(
                        "COTTONWOOD, AZ 86326",
                        "COTTONWOOD AZ 86326",
                        "COTTONWOOD, AZ",
                        "COTTONWOOD AZ"
                    )
                )
            }

            val addressLine1 = possibleLine1
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { !looksLikeCityStateZip(it) }

            val addressLine2 = possibleLine2
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { !looksLikeCityStateZip(it) }

            val cityLine = listOfNotNull(possibleLine2, possibleLine3)
                .firstOrNull { looksLikeCityStateZip(it) }

            val parsedCity = cityLine?.let { parseCityStateZip(it) }

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = parsedCity?.city,
                state = parsedCity?.state,
                zip = parsedCity?.zip
            )
        }

        return ShipToBlock(null, null, null, null, null, null)
    }

    private fun extractShipToCustomer(line: String): String? {
        val match = Regex("""SHIP\s*TO\s*:?\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(line)
            ?: return null

        return match.groupValues[1]
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun extractShipSide(line: String, leftSideMarkers: List<String>): String {
        val cleaned = line
            .replace(Regex("""\s+"""), " ")
            .replace(" ,", ",")
            .trim()

        for (marker in leftSideMarkers) {
            val match = Regex("""${Regex.escape(marker)}\s+(.+)$""", RegexOption.IGNORE_CASE)
                .find(cleaned)

            val value = match
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()

            if (!value.isNullOrBlank()) return value
        }

        return cleaned
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val current = decodeDoubledLine(lines[i])
                .replace(Regex("""\s+"""), " ")
                .trim()

            val next = lines.getOrNull(i + 1)
                ?.let { decodeDoubledLine(it) }
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val qty = parseLeadingQuantity(current) ?: continue
            val money = parseTrailingMoneyAndDate(current) ?: continue

            /*
             * Important:
             * DFS OCR often pollutes the main item row with email/address fragments.
             * The cleaner SKU is usually on the following PACK | SKU DFS | / EACH | SKU DFS | line.
             * So we intentionally check the bar/pipe row first.
             */
            val sku = chooseBestSku(
                candidates = listOfNotNull(
                    parseSkuFromBarLine(next),
                    parseSkuAfterUnitToken(current),
                    parseSkuFromBarLine(current),
                    parseSkuFromLine(current),
                    parseSkuFromLine(next)
                )
            ) ?: continue

            val description = ItemMapper.getItemDescription(sku).takeIf { it.isNotBlank() }
                ?: extractDescriptionFromBarLine(next, sku).ifBlank { null }

            val key = "$sku|$qty|${money.unitPrice}"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = qty,
                    unitPrice = money.unitPrice
                )
            )
        }

        return items
    }

    private fun chooseBestSku(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null

        val cleaned = candidates
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .filterNot { it.contains("@") }
            .filterNot { it.contains("_") }
            .filterNot { it.contains(".") }
            .distinct()

        if (cleaned.isEmpty()) return null

        val mapped = cleaned.firstOrNull { ItemMapper.getItemDescription(it).isNotBlank() }
        if (mapped != null) return mapped

        return cleaned.firstOrNull()
    }

    private fun parseLeadingQuantity(line: String): Double? {
        return Regex("""^\s*([\d,]+)\b""")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }

    private fun parseTrailingMoneyAndDate(line: String): MoneyAndDate? {
        val match = Regex(
            """([\d,]+\.\d{2,4})\s+([\d,]+\.\d{2})\s+(\d{2}/\d{2}/\d{2})\)?\s*$""",
            RegexOption.IGNORE_CASE
        ).find(line) ?: return null

        val unitPrice = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val ext = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        val dueDate = match.groupValues[3]

        return MoneyAndDate(unitPrice, ext, dueDate)
    }

    private fun parseSkuFromBarLine(line: String): String? {
        Regex(
            """\|\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)(?:\s+DFS)?\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(line)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.uppercase()
            ?.let { return it }

        return null
    }

    private fun parseSkuAfterUnitToken(line: String): String? {
        Regex(
            """\b(?:PACK|EACH)\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})(?:\s+DFS)?\b""",
            RegexOption.IGNORE_CASE
        ).find(line)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.uppercase()
            ?.let { return it }

        return null
    }

    private fun parseSkuFromLine(line: String): String? {
        Regex(
            """\b([A-Z]{2,}[A-Z0-9]*(?:-[A-Z0-9]+){2,})(?:\s+DFS)?\b""",
            RegexOption.IGNORE_CASE
        ).find(line)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.uppercase()
            ?.let { return it }

        return null
    }

    private fun extractDescriptionFromBarLine(line: String, sku: String): String {
        val normalized = normalizeForMatch(line)

        val match = Regex(
            """\b(PACK|EACH)\s*\|\s*(.*?)\s*\|\s*${Regex.escape(sku)}(?:\s+DFS)?\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(normalized)

        return match?.groupValues?.get(2)?.trim().orEmpty()
    }

    private fun looksLikeCityStateZip(line: String): Boolean {
        return Regex(
            """.*?,?\s+[A-Z]{2}\s+\d{5}(?:-\d{4})?\s*$""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(line.trim())
    }

    private fun parseCityStateZip(line: String): CityStateZip {
        val cleaned = line
            .replace(Regex("""\s+"""), " ")
            .replace(" ,", ",")
            .trim()

        val withComma = Regex(
            """^(.*?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        if (withComma != null) {
            return CityStateZip(
                city = withComma.groupValues[1].trim(),
                state = withComma.groupValues[2].trim().uppercase(),
                zip = withComma.groupValues[3].trim()
            )
        }

        val withoutComma = Regex(
            """^(.*?)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        return if (withoutComma != null) {
            CityStateZip(
                city = withoutComma.groupValues[1].trim(),
                state = withoutComma.groupValues[2].trim().uppercase(),
                zip = withoutComma.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private fun decodeDoubledLine(line: String): String {
        if (!isProbablyDoubledOcr(line)) {
            return line
                .replace(Regex("""\s+"""), " ")
                .replace(" ,", ",")
                .trim()
        }

        return line
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { undoubleToken(it) }
            .replace("::", ":")
            .replace("--", "-")
            .replace(" ,", ",")
            .replace(",,", ",")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isProbablyDoubledOcr(line: String): Boolean {
        val compact = line.filterNot { it.isWhitespace() }
        if (compact.length < 8) return false

        var paired = 0
        var checked = 0

        var i = 0
        while (i + 1 < compact.length) {
            checked++
            if (compact[i] == compact[i + 1]) paired++
            i += 2
        }

        if (checked == 0) return false

        val ratio = paired.toDouble() / checked.toDouble()
        return ratio >= 0.45
    }

    private fun normalizeForMatch(line: String): String {
        var s = decodeDoubledLine(line)

        s = s.replace(Regex("""([A-Za-z])\1+""")) { it.groupValues[1] }
        s = s.replace("::", ":")
        s = s.replace("--", "-")
        s = s.replace(Regex("""\s+"""), " ")
        s = s.replace(" ,", ",")
        s = s.trim()

        return s.uppercase()
    }

    private fun undoubleToken(token: String): String {
        val sb = StringBuilder()
        var i = 0

        while (i < token.length) {
            sb.append(token[i])

            if (i + 1 < token.length && token[i] == token[i + 1]) {
                i += 2
            } else {
                i += 1
            }
        }

        return sb.toString()
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

    private data class MoneyAndDate(
        val unitPrice: Double,
        val extended: Double,
        val dueDate: String
    )
}