package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import com.jay.parser.pdf.PdfLine

class MicroEssentialLaboratoryLayoutStrategy : BaseLayoutStrategy(), PositionedLayoutStrategy {
    override val name: String = "Micro Essential Laboratory"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("MICROESSENTIALLABORATORYINC") &&
                text.contains("VENDORSHIPBILLTO") &&
                text.contains("PURCHASINGAGREEMENT")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("MICROESSENTIALLABORATORYINC")) score += 240
        if (text.contains("4224AVENUEH")) score += 200
        if (text.contains("VENDORSHIPBILLTO")) score += 170
        if (text.contains("PURCHASINGAGREEMENT")) score += 140
        if (text.contains("BILLTOEMAIL")) score += 110
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = normalizedLines(lines)
        return parseFields(clean, extractShipToFromText(clean))
    }

    override fun parsePositioned(lines: List<PdfLine>): ParsedPdfFields {
        val clean = normalizedLines(lines.map(PdfLine::text))
        val shipTo = extractShipToFromPositionedLines(lines)
            ?: extractShipToFromText(clean)
        return parseFields(clean, shipTo)
    }

    private fun parseFields(lines: List<String>, shipTo: ShipTo?): ParsedPdfFields {
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = lines.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = shipTo?.name,
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = shipTo?.addressLine2,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(lines)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[4]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = match.groupValues[3].uppercase()
        )
    }

    private fun extractShipToFromPositionedLines(lines: List<PdfLine>): ShipTo? {
        val headerIndex = lines.indexOfFirst { it.text.contains("SHIP & BILL TO", ignoreCase = true) }
        if (headerIndex < 0) return null

        val values = lines
            .drop(headerIndex + 1)
            .takeWhile { !it.text.contains("Payment Terms", ignoreCase = true) }
            .mapNotNull(::rightColumnText)
            .filterNot { value ->
                value.startsWith("Phone", ignoreCase = true) ||
                        value.matches(PHONE_PATTERN) ||
                        value.equals("Bill-To Email", ignoreCase = true) ||
                        value.contains('@')
            }

        return parseShipToValues(values)
    }

    private fun extractShipToFromText(lines: List<String>): ShipTo? {
        val headerIndex = lines.indexOfFirst { it.contains("SHIP & BILL TO", ignoreCase = true) }
        if (headerIndex < 0) return null

        val values = lines
            .drop(headerIndex + 1)
            .takeWhile { !it.contains("Payment Terms", ignoreCase = true) }
            .mapNotNull(::rightColumnFallback)
            .filterNot { value ->
                value.startsWith("Phone", ignoreCase = true) ||
                        value.matches(PHONE_PATTERN) ||
                        value.equals("Bill-To Email", ignoreCase = true) ||
                        value.contains('@')
            }

        return parseShipToValues(values)
    }

    private fun parseShipToValues(values: List<String>): ShipTo? {
        val cityIndex = values.indexOfFirst { CITY_STATE_ZIP_PATTERN.matches(it) }
        if (cityIndex < 2) return null

        val cityMatch = CITY_STATE_ZIP_PATTERN.matchEntire(values[cityIndex]) ?: return null
        val addressLines = values.subList(1, cityIndex)

        return ShipTo(
            name = values.first(),
            addressLine1 = addressLines.first(),
            addressLine2 = addressLines.drop(1).joinToString(" ").ifBlank { null },
            city = cityMatch.groupValues[1].trim(),
            state = normalizeState(cityMatch.groupValues[2]),
            zip = cityMatch.groupValues[3]
        )
    }

    private fun rightColumnText(line: PdfLine): String? {
        val tokens = line.tokens
            .filter { it.x >= SHIP_TO_COLUMN_X }
            .sortedBy { it.x }
        if (tokens.isEmpty()) return null

        return buildString {
            tokens.forEachIndexed { index, token ->
                val text = token.text.trim()
                if (text.isEmpty()) return@forEachIndexed

                if (index > 0) {
                    val previous = tokens[index - 1]
                    if (token.x - (previous.x + previous.width) > WORD_GAP) append(' ')
                }
                append(text)
            }
        }.replace(Regex("""\s+"""), " ").trim().ifBlank { null }
    }

    private fun rightColumnFallback(line: String): String? {
        val prefix = LEFT_COLUMN_PREFIXES.firstOrNull { pattern -> pattern.containsMatchIn(line) }
        if (prefix != null) {
            return prefix.replaceFirst(line, "").trim().ifBlank { null }
        }

        return line.takeIf { it.contains('@') }?.trim()
    }

    private fun normalizedLines(lines: List<String>): List<String> = nonBlankLines(lines).map { line ->
        line.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun normalizeState(value: String): String = when (value.trim().uppercase()) {
        "NEW YORK" -> "NY"
        else -> value.trim().uppercase()
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val name: String,
        val addressLine1: String,
        val addressLine2: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "MICRO ESSENTIAL LABO"
        const val SHIP_TO_COLUMN_X = 300f
        const val WORD_GAP = 2.5f

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*#\s*([A-Z0-9-]+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+\$?([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),\s+([A-Z][A-Z ]+?)\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val PHONE_PATTERN = Regex("""^[\d() .+-]{7,}$""")
        val LEFT_COLUMN_PREFIXES = listOf(
            Regex("""^Precision Laboratories,\s*Inc\.\s+""", RegexOption.IGNORE_CASE),
            Regex("""^415 S Airpark Road\s+""", RegexOption.IGNORE_CASE),
            Regex("""^Cottonwood,\s*AZ\s+86326\s+""", RegexOption.IGNORE_CASE),
            Regex("""^Phone:\s*928-649-9833\s+""", RegexOption.IGNORE_CASE),
            Regex("""^orders@preclaboratories\.com\s+""", RegexOption.IGNORE_CASE)
        )
    }
}
