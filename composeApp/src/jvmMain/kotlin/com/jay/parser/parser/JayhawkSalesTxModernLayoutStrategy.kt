package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfLine
import com.jay.parser.pdf.PdfToken
import kotlin.math.abs

class JayhawkSalesTxModernLayoutStrategy : BaseLayoutStrategy(), PositionedLayoutStrategy {
    override val name = "JAYHAWK SALES TX (modern)"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("").uppercase().replace(Regex("""\s+"""), "")
        return text.contains("INFO@JAYHAWKSALES.COM") &&
            text.contains("2613INDUSTRIALLN") &&
            text.contains("GARLAND,TX75041") &&
            text.contains("PURCHASEORDERDETAILS") &&
            text.contains("#PRODUCTORSERVICEDESCRIPTIONQTYRATEAMOUNT")
    }

    override fun score(lines: List<String>): Int = if (matches(lines)) 1000 else 0

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val items = clean.dropWhile { !ITEM_HEADER.matches(it) }.drop(1)
            .takeWhile { !it.startsWith("Total", ignoreCase = true) && !it.startsWith("Note to vendor", ignoreCase = true) }
            .mapNotNull { line ->
                val row = ITEM_ROW.matchEntire(line) ?: return@mapNotNull null
                val (description, rawQuantity, rawPrice, rawAmount) = row.destructured
                val sku = SKU.find(description)?.value ?: return@mapNotNull null
                val quantity = number(rawQuantity) ?: return@mapNotNull null
                val price = number(rawPrice) ?: return@mapNotNull null
                val amount = number(rawAmount) ?: return@mapNotNull null
                if (quantity <= 0 || abs(quantity * price - amount) > 0.02) return@mapNotNull null
                item(sku.uppercase(), description.trim(), quantity, price)
            }
        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { PO_NUMBER.find(it)?.groupValues?.get(1) },
            terms = CustomerMapper.lookupCustomer(CUSTOMER_ID)?.terms,
            items = items
        )
    }

    override fun parsePositioned(lines: List<PdfLine>): ParsedPdfFields {
        val parsed = parse(lines.map(PdfLine::text))
        val headerIndex = lines.indexOfFirst { SHIP_HEADER.matches(it.text.trim()) }
        if (headerIndex < 0) return parsed
        val headerTokens = lines[headerIndex].tokens.sortedBy(PdfToken::x)
        val shipX = headerTokens.zipWithNext()
            .maxByOrNull { (left, right) -> right.x - left.x - left.width }?.second?.x ?: return parsed
        val shipRows = lines.drop(headerIndex + 1)
            .takeWhile { !it.text.contains("Purchase Order details", ignoreCase = true) && !ITEM_HEADER.matches(it.text.trim()) }
            .map { rebuildText(it.tokens.filter { token -> token.x >= shipX - 1f }) }
            .filter(String::isNotBlank)
        val cityIndex = shipRows.indexOfFirst { LOCALITY.matches(it) }
        if (cityIndex < 2) return parsed
        val cityMatch = LOCALITY.matchEntire(shipRows[cityIndex]) ?: return parsed
        return parsed.copy(
            shipToCustomer = shipRows.first(),
            addressLine1 = shipRows[1],
            addressLine2 = shipRows.subList(2, cityIndex).joinToString(", ").ifBlank { null },
            city = cityMatch.groupValues[1].trim(),
            state = cityMatch.groupValues[2].uppercase(),
            zip = cityMatch.groupValues[3]
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

    private fun number(value: String): Double? = value.replace(",", "").toDoubleOrNull()

    private companion object {
        const val CUSTOMER_ID = "JAYHAWK SALES TX"
        val PO_NUMBER = Regex("""\bPO\s+NO\.?:\s*([A-Z0-9][A-Z0-9-]*)""", RegexOption.IGNORE_CASE)
        val ITEM_HEADER = Regex("""#\s+Product\s+or\s+service\s+Description\s+Qty\s+Rate\s+Amount""", RegexOption.IGNORE_CASE)
        val ITEM_ROW = Regex(
            """^\d+\.\s+\S+\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+\$([\d,]+\.\d{2,3})\s+\$([\d,]+\.\d{2})$""",
            RegexOption.IGNORE_CASE
        )
        val SKU = Regex("""\b[A-Z0-9]+(?:-[A-Z0-9]+){2,}\b""", RegexOption.IGNORE_CASE)
        val SHIP_HEADER = Regex("""Vendor\s+Ship\s*to""", RegexOption.IGNORE_CASE)
        val LOCALITY = Regex("""(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)(?:\s+USA)?""", RegexOption.IGNORE_CASE)
    }
}
