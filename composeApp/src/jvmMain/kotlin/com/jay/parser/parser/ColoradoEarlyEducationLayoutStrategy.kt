package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import com.jay.parser.pdf.PdfLine
import com.jay.parser.pdf.PdfToken
import kotlin.math.abs

class ColoradoEarlyEducationLayoutStrategy : BaseLayoutStrategy(), PositionedLayoutStrategy {
    private companion object {
        const val CUSTOMER_ID = "COLORADO EARLY EDUCA"
        const val SHIP_COLUMN_MIN_X = 250f
        const val EXTENSION_TOLERANCE = 0.02

        val PO_NUMBER_PATTERN = Regex("""\bPO\s*#\s*(\d{8})\b""", RegexOption.IGNORE_CASE)
        val EIGHT_DIGIT_NUMBER = Regex("""\b\d{8}\b""")
        val CUSTOMER_NAME_PATTERN = Regex(
            """Colorado Early Education Network""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_STREET_PATTERN = Regex("""\b\d+\s+S\.?\s*6(?:th)?\s+St\b""", RegexOption.IGNORE_CASE)
        val STREET_PATTERN = Regex("""\d+\s+.+""")
        val CITY_STATE_ZIP_PATTERN = Regex(
            """([A-Za-z][A-Za-z .'-]+),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(\d+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+)*-?)\s+.+?\s+([\d,]+\.\d{2,3})\s+([\d,]+\.\d{2,3})$""",
            RegexOption.IGNORE_CASE
        )
        val SKU_CONTINUATION_PATTERN = Regex("""[A-Z0-9]+""", RegexOption.IGNORE_CASE)
    }

    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("COLORADOEARLYEDUCATIONNETWORK") &&
                text.contains("2021CLUBHOUSEDRSTE102") &&
                text.contains("PURCHASEORDER") &&
                text.contains("REQUISITIONER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("COLORADOEARLYEDUCATIONNETWORK")) score += 220
        if (text.contains("2021CLUBHOUSEDRSTE102")) score += 180
        if (text.contains("GREELEYCO80634")) score += 140
        if (text.contains("PURCHASEORDER")) score += 100
        if (text.contains("REQUISITIONER")) score += 80
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::normalizeLine)
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseMergedShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo?.customer,
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = null,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    override fun parsePositioned(lines: List<PdfLine>): ParsedPdfFields {
        val parsed = parse(lines.map(PdfLine::text))
        val shipTo = parsePositionedShipTo(lines) ?: return parsed

        return parsed.copy(
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        return lines.firstNotNullOfOrNull { line ->
            PO_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
        } ?: lines
            .dropWhile { !compact(it).contains("PONUMBER") }
            .firstNotNullOfOrNull { line -> EIGHT_DIGIT_NUMBER.find(line)?.value }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (index in lines.indices) {
            val row = ITEM_ROW_PATTERN.find(lines[index]) ?: continue
            val quantity = parseNumber(row.groupValues[1]) ?: continue
            var sku = row.groupValues[2].uppercase()
            val unitPrice = parseNumber(row.groupValues[3]) ?: continue
            val extension = parseNumber(row.groupValues[4]) ?: continue

            if (sku.endsWith('-')) {
                val continuation = lines.getOrNull(index + 1)
                    ?.let { SKU_CONTINUATION_PATTERN.matchEntire(it) }
                    ?.value
                    ?: continue
                sku += continuation.uppercase()
            }

            if (quantity <= 0.0 || unitPrice <= 0.0 ||
                abs(quantity * unitPrice - extension) > EXTENSION_TOLERANCE
            ) {
                continue
            }

            val description = ItemMapper.getItemDescription(sku)
            if (description.isBlank()) continue

            add(
                item(
                    sku = sku,
                    description = description,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun parsePositionedShipTo(lines: List<PdfLine>): ShipTo? {
        val headerIndex = lines.indexOfFirst {
            compact(it.text).contains("BILLTOSHIPTO")
        }
        if (headerIndex < 0) return null

        val shipColumnX = lines[headerIndex].tokens
            .filter { token -> token.x > SHIP_COLUMN_MIN_X }
            .minOfOrNull(PdfToken::x)
            ?: return null

        val rightColumn = lines
            .drop(headerIndex + 1)
            .takeWhile { line -> !compact(line.text).contains("COMMENTSORSPECIALINSTRUCTIONS") }
            .map { line -> rebuildColumnText(line.tokens.filter { it.x >= shipColumnX - 1f }) }
            .filter(String::isNotBlank)

        return interpretShipTo(rightColumn)
    }

    private fun parseMergedShipTo(lines: List<String>): ShipTo? {
        val headerIndex = lines.indexOfFirst { compact(it).contains("BILLTOSHIPTO") }
        if (headerIndex < 0) return null

        val window = lines.drop(headerIndex + 1).take(6)
        val customer = window.firstNotNullOfOrNull { line ->
            val matches = CUSTOMER_NAME_PATTERN.findAll(line).toList()
            matches.lastOrNull()?.value
        }
        val address = window.firstNotNullOfOrNull { line ->
            SHIP_STREET_PATTERN.find(line)?.value
        }
        val cityMatch = window.firstNotNullOfOrNull { line ->
            CITY_STATE_ZIP_PATTERN.findAll(line).lastOrNull()
        }

        if (customer == null || address == null || cityMatch == null) return null
        return ShipTo(
            customer = customer,
            addressLine1 = normalizeOrdinal(address),
            city = cityMatch.groupValues[1].trim(),
            state = cityMatch.groupValues[2].uppercase(),
            zip = cityMatch.groupValues[3]
        )
    }

    private fun interpretShipTo(lines: List<String>): ShipTo? {
        val customer = lines.firstOrNull { CUSTOMER_NAME_PATTERN.matches(it) } ?: return null
        val address = lines.firstOrNull { STREET_PATTERN.matches(it) } ?: return null
        val cityMatch = lines.firstNotNullOfOrNull(CITY_STATE_ZIP_PATTERN::find) ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = normalizeOrdinal(address),
            city = cityMatch.groupValues[1].trim(),
            state = cityMatch.groupValues[2].uppercase(),
            zip = cityMatch.groupValues[3]
        )
    }

    private fun normalizeOrdinal(value: String): String = value
        .replace(Regex("""\b6\s+St\b""", RegexOption.IGNORE_CASE), "6th St")
        .trim()

    private fun rebuildColumnText(tokens: List<PdfToken>): String {
        val sorted = tokens.sortedBy(PdfToken::x)
        if (sorted.isEmpty()) return ""

        return buildString {
            var previous: PdfToken? = null
            for (token in sorted) {
                val text = token.text.trim()
                if (text.isEmpty()) continue
                val prior = previous
                if (prior != null && token.x - (prior.x + prior.width) > 1f) append(' ')
                append(text)
                previous = token
            }
        }.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseNumber(value: String): Double? = value.replace(",", "").toDoubleOrNull()

    private fun normalizeLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val customer: String,
        val addressLine1: String,
        val city: String,
        val state: String,
        val zip: String
    )

}
