package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CmRepresentacionesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "C&M Representaciones"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CMREPRESENTACIONESSADECV") &&
                text.contains("PEDIDONO") &&
                text.contains("9950MARCONIDR")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CMREPRESENTACIONESSADECV")) score += 160
        if (text.contains("CMREPRESPRODIGYNETMX")) score += 100
        if (text.contains("PEDIDONO")) score += 70
        if (text.contains("9950MARCONIDR")) score += 80
        if (text.contains("SANDIEGO")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)

        return ParsedPdfFields(
            customerName = "C&M REPRESENTACIO",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "C&M REPRESENTACIONES, S.A. DE C.V.",
            addressLine1 = "9950 MARCONI DR SUITE 103",
            addressLine2 = null,
            city = "SAN DIEGO",
            state = "CA",
            zip = "92154",
            terms = "Contado",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        val match = ORDER_NUMBER_PATTERN.find(joined)
            ?: FALLBACK_ORDER_NUMBER_PATTERN.find(joined)
            ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_PATTERN.find(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val unitPrice = match.groupValues[4].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { match.groupValues[3].trim() },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun cleanLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """PEDIDO\s+No\.?.{0,80}?\b(\d{3})\s*/\s*(\d{4})""",
            RegexOption.IGNORE_CASE
        )
        val FALLBACK_ORDER_NUMBER_PATTERN = Regex("""\b(\d{3})\s*/\s*(\d{4})\b""")
        val ITEM_PATTERN = Regex(
            """^([A-Z0-9.-]+)\s+([\d,]+(?:\.\d+)?)\s+(.+?)\s*USD\s+([\d,]+\.\d{2,3})\s+\d+(?:\.\d+)?%\s+USD\s+[\d,]+\.\d{2}$""",
            RegexOption.IGNORE_CASE
        )
    }
}
