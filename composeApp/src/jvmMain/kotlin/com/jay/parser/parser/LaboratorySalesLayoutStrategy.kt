package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class LaboratorySalesLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Laboratory Sales & Service"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("LABORATORYSALESSERVICELLC") &&
                text.contains("10COUNTYLINEROADSTE22") &&
                text.contains("TBDPRECISIONLAB") &&
                text.contains("VENDSKUSKUPRODUCTSHELFLIFEQUOTEQTYPRICETOTAL")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("LABORATORYSALESSERVICELLC")) score += 220
        if (text.contains("10COUNTYLINEROADSTE22")) score += 190
        if (text.contains("BRANCHBURGNJ08876")) score += 170
        if (text.contains("TBDPRECISIONLAB")) score += 150
        if (text.contains("VENDSKUSKUPRODUCTSHELFLIFEQUOTEQTYPRICETOTAL")) score += 130
        if (text.contains("VENDORPURCHASEORDER")) score += 100
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
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Laboratory Sales & Service LLC",
            addressLine1 = "10 County Line Road, Ste 22",
            city = "Branchburg",
            state = "NJ",
            zip = "08876",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[4]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[5]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank {
                match.groupValues[3].trim()
            },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "LABORATORY SALES &"

        val ORDER_NUMBER_PATTERN = Regex(
            """\b\d{1,2}/\d{1,2}/\d{4}\s+(\d+)\s*$"""
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+(\S+)\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
