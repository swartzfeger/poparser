package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class WilkensAndersonLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "WILKENS ANDERSON"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("WILKENSANDERSONCO") &&
                text.contains("1190NORTHKILBOURNAVE") &&
                text.contains("POACKWACOLABCOM") &&
                text.contains("ORDERQTYPRODUCTDESCRIPTIONUNITPRICENET")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("WILKENSANDERSONCO")) score += 220
        if (text.contains("1190NORTHKILBOURNAVE")) score += 190
        if (text.contains("POACKWACOLABCOM")) score += 170
        if (text.contains("ORDERQTYPRODUCTDESCRIPTIONUNITPRICENET")) score += 150
        if (text.contains("VENDOR12134")) score += 130
        if (text.contains("KATHYKING")) score += 110
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = "WILKENS-ANDERSON CO.",
            addressLine1 = "1190 NORTH KILBOURN AVE.",
            city = "CHICAGO",
            state = "IL",
            zip = "60651",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val quantity = parseNumber(row.groupValues[1]) ?: return@mapNotNull null
        val sourceProduct = row.groupValues[3].uppercase()
        val sourceDescription = row.groupValues[4].trim()
        val sku = resolveSku(sourceProduct, sourceDescription) ?: return@mapNotNull null
        val unitPrice = parseNumber(row.groupValues[5]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sourceDescription },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = row.groupValues[2].uppercase()
        )
    }

    private fun resolveSku(sourceProduct: String, description: String): String? {
        val normalizedDescription = compact(description)
        return when {
            sourceProduct == "0-12V-100" &&
                    normalizedDescription.contains("PAPERTESTPOTIODSTARCH") -> "160-12V-100"

            sourceProduct == "0-24-8X10" &&
                    normalizedDescription.contains("COBALTCHLORIDETESTPAPER") -> "250-24-8X10"

            ItemMapper.getAllSkus().contains(sourceProduct) -> sourceProduct
            else -> null
        }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "WILKENS ANDERSON"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bP/O\s*#\s*:\s*(P\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(\d+(?:\.\d+)?)\s*([A-Z]+)\s+(\S+)\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
