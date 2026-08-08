package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class AtheaLaboratoriesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Athea Laboratories"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("ATHEALABORATORIES") &&
                text.contains("VENDORNO904030") &&
                text.contains("7855NFAULKNERROAD") &&
                text.contains("ATHEAPOATHEACOM")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("ATHEALABORATORIES")) score += 180
        if (text.contains("VENDORNO904030")) score += 160
        if (text.contains("7855NFAULKNERROAD")) score += 140
        if (text.contains("ATHEAPOATHEACOM")) score += 120
        if (text.contains("ITEMCODEVENDORCODEDESCRIPTIONDUEDATEQTYUOMPRICETOTAL")) score += 80
        if (text.contains("ITEMNOVENDORNODESCRIPTIONDELIVERYDATEQUANTITYUOFMUNITPRICETOTAL")) score += 80
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_NAME)

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Athea Laboratories",
            addressLine1 = "7855 N. Faulkner Road",
            city = "Milwaukee",
            state = "WI",
            zip = "53224",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val unitPrice = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_NAME = "ATHEA LABORATORIES"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bORDER\s*NO\.?\s*:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )

        val ITEM_ROW_PATTERN = Regex(
            """^\d{6,}\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+\d{1,2}/\d{1,2}/\d{2,4}\s+([\d,]+(?:\.\d+)?)\s+EA\s+\$?([\d,]+(?:\.\d+)?)\s+\$?[\d,]+(?:\.\d+)?(?:\s*USD)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
