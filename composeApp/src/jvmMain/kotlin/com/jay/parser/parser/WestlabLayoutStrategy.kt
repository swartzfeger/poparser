package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class WestlabLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "WESTLAB"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WESTLABNORTHAMERICACANADA") &&
                text.contains("WESTLABVENDORPACKAGINGREQUIREMENTS") &&
                text.contains("BN830371035RT")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WESTLABNORTHAMERICACANADA")) score += 160
        if (text.contains("WESTLABVENDORPACKAGINGREQUIREMENTS")) score += 140
        if (text.contains("BN830371035RT")) score += 120
        if (text.contains("SALESWESTLABCOM")) score += 100
        if (text.contains("SUPPLIERDETAILS")) score += 80
        if (text.contains("WESTLABCODE")) score += 60
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
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
            },
            shipToCustomer = "Westlab North America (Canada)",
            addressLine1 = "#101-19050 25 Avenue",
            city = "Surrey",
            state = "BC",
            zip = "V3Z 3V2",
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
        const val CUSTOMER_NAME = "WESTLAB"

        val ORDER_NUMBER_PATTERN = Regex("""\bCPO\d{6}\b""", RegexOption.IGNORE_CASE)

        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+\d{3}-\d{4}\s+.+?\s+(?:\d{1,2}/\d{1,2}/\d{4}\s+)?(\d+(?:,\d{3})*)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
