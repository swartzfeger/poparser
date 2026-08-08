package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MedtronicSurgicalSolutionsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Medtronic Surgical Solutions"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SURGICALSOLUTIONS") &&
                text.contains("GBUOFCOVIDIENLP") &&
                text.contains("RSUSAPMEDTRONICCOM") &&
                text.contains("PURCHASEORDERNUMBER")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SURGICALSOLUTIONS")) score += 220
        if (text.contains("GBUOFCOVIDIENLP")) score += 200
        if (text.contains("RSUSAPMEDTRONICCOM")) score += 190
        if (text.contains("1000023169")) score += 180
        if (text.contains("ALANHOWEMEDTRONICCOM")) score += 170
        if (text.contains("MEDTRONICANDSUPPLIERAGREE")) score += 160
        if (text.contains("29011515")) score += 140
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
            shipToCustomer = "SURGICAL SOLUTIONS",
            addressLine1 = "195 McDermott Road",
            addressLine2 = "a GBU of COVIDIEN LP",
            city = "North Haven",
            state = "CT",
            zip = "06473",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (index in lines.indices) {
            val row = ITEM_ROW_PATTERN.matchEntire(lines[index]) ?: continue
            val quantity = parseNumber(row.groupValues[2]) ?: continue
            val unitPrice = parseNumber(row.groupValues[3]) ?: continue
            val sku = (index + 1..minOf(index + 5, lines.lastIndex))
                .firstNotNullOfOrNull { candidateIndex ->
                    VENDOR_ITEM_PATTERN.find(lines[candidateIndex])?.groupValues?.get(1)?.uppercase()
                }
                ?: continue

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "MEDTRONIC"

        val ORDER_NUMBER_PATTERN = Regex(
            """Purchase\s+Order\s+Number:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^(\d+)\s+[A-Z0-9]+(?:-[A-Z0-9]+)*\s+RAW\s+EA\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val VENDOR_ITEM_PATTERN = Regex(
            """Vnd\s+Item:\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
    }
}
