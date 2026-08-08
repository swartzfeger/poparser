package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class NascoWiLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Nasco Wisconsin"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("FORTATKINSONWI53538") &&
                text.contains("TALLISONNASCOEDUCATIONCOM") &&
                text.contains("PURCHASEORDERTOTAL")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("FORTATKINSONWI53538")) score += 160
        if (text.contains("TALLISONNASCOEDUCATIONCOM")) score += 140
        if (text.contains("628926093")) score += 120
        if (text.contains("PURCHASEORDERTOTAL")) score += 100
        if (text.contains("SPECIALPRICING")) score += 80
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
            shipToCustomer = "NASCO",
            addressLine1 = "901 Janesville Ave",
            city = "Fort Atkinson",
            state = "WI",
            zip = "53538",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        lines.firstNotNullOfOrNull { line ->
            CLEAN_ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
        }?.let { return it }

        for (line in lines) {
            val compactLine = compact(line)
            val match = DOUBLED_ORDER_NUMBER_PATTERN.find(compactLine) ?: continue
            val digits = collapseRepeatedPairs(match.groupValues[1])
            if (digits.length == 5) return "M$digits"
        }
        return null
    }

    private fun collapseRepeatedPairs(value: String): String {
        var current = value
        while (current.length % 2 == 0) {
            val pairs = current.chunked(2)
            if (pairs.any { it.length != 2 || it[0] != it[1] }) break
            current = pairs.joinToString("") { it[0].toString() }
        }
        return current
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val supplierQuantity = parseNumber(match.groupValues[1]) ?: return@mapNotNull null
        val supplierUom = match.groupValues[2].uppercase()
        val sku = match.groupValues[3].uppercase()
        val nascoUom = match.groupValues[5].uppercase()
        val unitCost = parseNumber(match.groupValues[7]) ?: return@mapNotNull null
        val extension = parseNumber(match.groupValues[8]) ?: return@mapNotNull null

        val supplierUnitPrice = if (supplierUom == nascoUom) {
            unitCost
        } else {
            extension / supplierQuantity
        }

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = supplierQuantity,
            unitPrice = supplierUnitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "NASCO  WI"

        val CLEAN_ORDER_NUMBER_PATTERN = Regex("""\bM\d{5}\b""", RegexOption.IGNORE_CASE)
        val DOUBLED_ORDER_NUMBER_PATTERN = Regex("""M{2,}(\d{10,})""")
        val ITEM_ROW_PATTERN = Regex(
            """^\*?\s*([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+.+?\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+([A-Z0-9]+)(?:\s+[A-Z])?\s+(\d*\.\d+)\s+([\d,]+\.\d+)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
