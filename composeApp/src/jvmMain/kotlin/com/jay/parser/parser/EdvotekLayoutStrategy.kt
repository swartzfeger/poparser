package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfLine
import com.jay.parser.pdf.PdfToken
import kotlin.math.abs

class EdvotekLayoutStrategy : BaseLayoutStrategy(), PositionedLayoutStrategy {
    override val name = "EDVOTEK, INC"

    override fun matches(lines: List<String>): Boolean {
        val clean = nonBlankLines(lines)
        val header = clean.indexOfFirst { it.equals("Vendor Ship To", ignoreCase = true) }
        return header > 0 &&
            clean.take(header).any { it.equals("Purchase Order", ignoreCase = true) } &&
            clean.take(header).any { BUYER_HEADER.containsMatchIn(it) } &&
            clean.any { ITEM_HEADER.matches(it) }
    }

    override fun score(lines: List<String>): Int = if (matches(lines)) 1000 else 0

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val orderNumber = clean
            .dropWhile { !PO_HEADER.matches(it) }
            .drop(1)
            .take(3)
            .firstNotNullOfOrNull { PO_VALUE.find(it)?.groupValues?.get(1) }
        val items = clean
            .dropWhile { !ITEM_HEADER.matches(it) }
            .drop(1)
            .takeWhile { !it.startsWith("Total ", ignoreCase = true) }
            .mapNotNull { line ->
                val row = ITEM_ROW.matchEntire(line) ?: return@mapNotNull null
                val (sku, rawQuantity, description, rawPrice, rawAmount) = row.destructured
                if (sku.equals("NOTE", ignoreCase = true)) return@mapNotNull null
                val quantity = rawQuantity.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                val unitPrice = rawPrice.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                val amount = rawAmount.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                if (quantity <= 0 || abs(quantity * unitPrice - amount) > 0.02) return@mapNotNull null
                item(sku.uppercase(), description, quantity, unitPrice)
            }
        return ParsedPdfFields(customerName = name, orderNumber = orderNumber, items = items)
    }

    override fun parsePositioned(lines: List<PdfLine>): ParsedPdfFields {
        val parsed = parse(lines.map(PdfLine::text))
        val header = lines.indexOfFirst { it.text.trim().equals("Vendor Ship To", ignoreCase = true) }
        if (header < 0) return parsed

        // Find the boundary between the Vendor and Ship To headings.
        val shipX = lines[header].tokens.sortedBy(PdfToken::x).zipWithNext()
            .maxByOrNull { (left, right) -> right.x - left.x - left.width }
            ?.second?.x ?: return parsed
        val shipTo = lines.drop(header + 1)
            .takeWhile { !it.text.contains("Receive By", ignoreCase = true) && !ITEM_HEADER.matches(it.text.trim()) }
            .map { line -> rebuildText(line.tokens.filter { it.x >= shipX - 1f }) }
            .filter(String::isNotBlank)
        val streetIndex = shipTo.indexOfFirst { STREET.matches(it) }
        if (streetIndex < 1) return parsed
        val localityIndex = shipTo.indices.drop(streetIndex + 1)
            .firstOrNull { CITY_STATE_ZIP.matches(shipTo[it]) } ?: return parsed
        val locality = CITY_STATE_ZIP.matchEntire(shipTo[localityIndex]) ?: return parsed
        val (city, state, zip) = locality.destructured
        val extraAddress = (shipTo.subList(1, streetIndex) + shipTo.subList(streetIndex + 1, localityIndex))
            .joinToString(", ").ifBlank { null }

        return parsed.copy(
            shipToCustomer = shipTo.first(),
            addressLine1 = shipTo[streetIndex],
            addressLine2 = extraAddress,
            city = city,
            state = state.uppercase(),
            zip = zip
        )
    }

    private fun rebuildText(tokens: List<PdfToken>): String = buildString {
        var previous: PdfToken? = null
        for (token in tokens.sortedBy(PdfToken::x)) {
            previous?.let { if (token.x - it.x - it.width > 0.8f) append(' ') }
            append(token.text)
            previous = token
        }
    }.trim()

    private companion object {
        val BUYER_HEADER = Regex("""^EDVOTEK\b""", RegexOption.IGNORE_CASE)
        val PO_HEADER = Regex("""Date\s+PO\s*#""", RegexOption.IGNORE_CASE)
        val PO_VALUE = Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\s+([A-Z0-9][A-Z0-9-]*)$""", RegexOption.IGNORE_CASE)
        val ITEM_HEADER = Regex("""Item\s+Quantity\s+Description\s+Options\s+Rate\s+Amount""", RegexOption.IGNORE_CASE)
        val ITEM_ROW = Regex(
            """^([A-Z0-9][A-Z0-9./-]*)\s+([\d,]+(?:\.\d+)?)\s+(.+?)\s+\$?([\d,]+\.\d{2,3})\s+\$?([\d,]+\.\d{2})$""",
            RegexOption.IGNORE_CASE
        )
        val STREET = Regex("""\d+\s+\S.*""")
        val CITY_STATE_ZIP = Regex("""(.+?),?\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""", RegexOption.IGNORE_CASE)
    }
}
