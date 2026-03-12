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
                    normalized.contains("DFS")

        val hasPurchaseOrder =
            raw.contains("PPUURRCCHHAASSEE OORRDDEERR") ||
                    normalized.contains("PURCHASE ORDER")

        val hasShipTo =
            raw.contains("SSHHIIPP TTOO::") ||
                    raw.contains("SHIP TO:") ||
                    normalized.contains("SHIP TO")

        return hasDfs && hasPurchaseOrder && hasShipTo
    }

    override fun score(lines: List<String>): Int {
        val raw = lines.joinToString("\n").uppercase()
        val normalized = lines.joinToString("\n") { normalizeForMatch(it) }

        var score = 0

        if (raw.contains("DDFFSS") || normalized.contains("DFS")) score += 80
        if (raw.contains("DDIIVVEERRSSIIFFIIEEDD FFOOOODDSSEERRVVIICCEE") || normalized.contains("DIVERSIFIED FOODSERVICE")) score += 100
        if (raw.contains("PPUURRCCHHAASSEE OORRDDEERR") || normalized.contains("PURCHASE ORDER")) score += 60
        if (raw.contains("SSHHIIPP TTOO::") || normalized.contains("SHIP TO")) score += 50
        if (normalized.contains("AP NEW JERSEY")) score += 60
        if (normalized.contains("101 MOUNT HOLLY BYPASS")) score += 50
        if (normalized.contains("LUMBERTON, NJ 08048")) score += 50
        if (normalized.contains("PREPAID")) score += 25
        if (raw.contains("145-144V-100 DFS") || raw.contains("PAA-50-1V-100 DFS")) score += 40

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
        val joinedRaw = lines.joinToString("\n")
        val joinedNormalized = lines.joinToString("\n") { normalizeForMatch(it) }

        Regex("""\b(\d{6}-\d{2})\b""")
            .find(joinedRaw)
            ?.groupValues?.get(1)
            ?.let { return it }

        Regex("""\b(\d{12}--\d{4})\b""")
            .find(joinedRaw)
            ?.groupValues?.get(1)
            ?.let { doubled ->
                return undoubleToken(doubled)
            }

        Regex("""PURCHASE\s+ORDER\s+NO\.?\s*[:#]?\s*(\d{6}-\d{2})""", RegexOption.IGNORE_CASE)
            .find(joinedNormalized)
            ?.groupValues?.get(1)
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
        for (i in lines.indices) {
            val raw = lines[i].uppercase()

            val shipToLine =
                raw.contains("SSHHIIPP TTOO:: AAPP NNEEWW JJEERRSSEEYY") ||
                        raw.contains("SHIP TO: AP NEW JERSEY")

            if (!shipToLine) continue

            val line1 = lines.getOrNull(i + 1)?.let { decodeDoubledLine(it) }
            val line2 = lines.getOrNull(i + 2)?.let { decodeDoubledLine(it) }

            val addressLine1 = line1
                ?.substringAfter("PRECISION LABS", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val cityLine = line2
                ?.substringAfter("415 AIRPARK ROAD", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val parsedCity = cityLine?.let { parseCityStateZip(it) }

            return ShipToBlock(
                shipToCustomer = "AP NEW JERSEY",
                addressLine1 = addressLine1,
                addressLine2 = null,
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

            val qty = parseLeadingQuantity(current) ?: continue
            val money = parseTrailingMoneyAndDate(current) ?: continue
            val sku = parseSkuFromBarLine(next) ?: continue

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

    private fun parseLeadingQuantity(line: String): Double? {
        return Regex("""^\s*([\d,]+)\b""")
            .find(line)
            ?.groupValues?.get(1)
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
        val match = Regex(
            """\|\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+DFS\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(line)

        return match?.groupValues?.get(1)?.trim()?.uppercase()
    }

    private fun extractDescriptionFromBarLine(line: String, sku: String): String {
        val normalized = normalizeForMatch(line)

        val match = Regex(
            """\b(PACK|EACH)\s*\|\s*(.*?)\s*\|\s*${Regex.escape(sku)}\s+DFS\s*\|""",
            RegexOption.IGNORE_CASE
        ).find(normalized)

        return match?.groupValues?.get(2)?.trim().orEmpty()
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

    private fun decodeDoubledLine(line: String): String {
        return line
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { undoubleToken(it) }
            .replace(" ,", ",")
            .replace(",,", ",")
            .trim()
    }

    private fun normalizeForMatch(line: String): String {
        var s = line

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