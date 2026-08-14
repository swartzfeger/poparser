package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class OsceolaSupplyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "OSCEOLA SUPPLY, INC"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("OSCEOLASUPPLYINC") &&
                text.contains("915COMMERCEBLVD") &&
                text.contains("MIDWAYFL32343") &&
                text.contains("VENDPARTDESCRIPTIONQUANTPRICEEXTENSION") &&
                text.contains("TP101145144V100")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("OSCEOLASUPPLYINC")) score += 230
        if (text.contains("915COMMERCEBLVD")) score += 200
        if (text.contains("MIDWAYFL32343")) score += 180
        if (text.contains("VENDPARTDESCRIPTIONQUANTPRICEEXTENSION")) score += 160
        if (text.contains("TP101145144V100")) score += 140
        if (text.contains("PURCHASEORDERNORCVNGDATE")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "OSCEOLA SUPPLY, INC",
            addressLine1 = "915 Commerce Blvd",
            city = "Midway",
            state = "FL",
            zip = "32343",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val headerIndex = lines.indexOfFirst { line ->
            compact(line) == "PURCHASEORDERNORCVNGDATE"
        }
        if (headerIndex >= 0) {
            val candidate = lines.getOrNull(headerIndex + 1).orEmpty()
            val dateStart = PO_DATE_PATTERN.find(candidate)?.range?.first ?: candidate.length
            val orderDigits = candidate.take(dateStart).filter(Char::isDigit)
            if (orderDigits.length >= 6) return orderDigits.take(6)
        }

        return lines.firstNotNullOfOrNull { line ->
            OCR_ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.find(line) ?: return@mapNotNull null
        val sku = row.groupValues[1]
            .replace(Regex("""\s+"""), "")
            .uppercase()
        val rowRemainder = row.groupValues[2]
        val unitPrice = DECIMAL_PATTERN.findAll(rowRemainder)
            .firstOrNull()
            ?.value
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return@mapNotNull null
        val quantity = CASE_QUANTITY_PATTERN.find(rowRemainder)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: DEFAULT_CASE_QUANTITY

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = "CS"
        )
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "OSCELOA SUPPLY, INC"
        const val DEFAULT_CASE_QUANTITY = 1.0

        val OCR_ORDER_NUMBER_PATTERN = Regex(
            """\b(\d{6})\s+PAGE\s*1\b""",
            RegexOption.IGNORE_CASE
        )
        val PO_DATE_PATTERN = Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")
        val ITEM_ROW_PATTERN = Regex(
            """\b(145\s*-\s*144V\s*-\s*100)\b\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val CASE_QUANTITY_PATTERN = Regex(
            """\b(\d+)\s*C[S5]\b""",
            RegexOption.IGNORE_CASE
        )
        val DECIMAL_PATTERN = Regex("""\b\d[\d,]*\.\d{2,4}\b""")
    }
}
