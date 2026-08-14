package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SaintLukesHealthLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "SAINT LUKES HEALTH"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SAINTLUKESHOSPITALOFKANSASCITY") &&
                text.contains("SLHRECEIVINGSLHWAREHOUSE") &&
                text.contains("1880NCORRINGTONAVESTE150") &&
                text.contains("POVENDORCOPYRPT")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SAINTLUKESHOSPITALOFKANSASCITY")) score += 220
        if (text.contains("SLHRECEIVINGSLHWAREHOUSE")) score += 200
        if (text.contains("1880NCORRINGTONAVESTE150")) score += 180
        if (text.contains("CENTRALACCTPAYSAINTLUKESORG")) score += 150
        if (text.contains("POVENDORCOPYRPT")) score += 130
        if (text.contains("TAXID440545297")) score += 110
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
            shipToCustomer = "SLH RECEIVING (SLH WAREHOUSE)",
            addressLine1 = "1880 N CORRINGTON AVE STE 150",
            city = "KANSAS CITY",
            state = "MO",
            zip = "64120-1900",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val row = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = row.groupValues[1].uppercase()
        val quantity = parseNumber(row.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseNumber(row.groupValues[4]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = row.groupValues[3].uppercase()
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "SAINT LUKES HEALTH"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*No:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+([\d,]+(?:\.\d+)?)\s+([A-Z]+)\s+(\$[\d,]+(?:\.\d+)?)\s+\$[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
