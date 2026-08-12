package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class RilabLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "RILAB"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CARLOSRIVASPADILLA") &&
                text.contains("LIMITADA") &&
                text.contains("RUT773143803") &&
                text.contains("RILABCL") &&
                text.contains("ORDENDECOMPRA") &&
                text.contains("HUECHURABACLRM8581151")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CARLOSRIVASPADILLA") && text.contains("LIMITADA")) score += 200
        if (text.contains("RUT773143803")) score += 180
        if (text.contains("RILABCL")) score += 160
        if (text.contains("HUECHURABACLRM8581151")) score += 140
        if (text.contains("ORDENDECOMPRA")) score += 120
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
            shipToCustomer = "Carlos Rivas Padilla y Compañía Limitada",
            addressLine1 = "Av del Valle Sur 570",
            addressLine2 = "Of 102, Ciudad Empresarial, Chile",
            city = "Huechuraba",
            state = "RM",
            zip = "8581151",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseSpanishNumber(match.groupValues[2]) ?: return@mapNotNull null
        val unitPrice = parseSpanishNumber(match.groupValues[3]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseSpanishNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace("Ñ", "N")
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "RILAB"

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDEN\s+DE\s+COMPRA\s*#\s*(P\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\[H40([A-Z0-9]+(?:-[A-Z0-9]+){2,})]\s*.+?\s+(\d+[.,]\d+)\s*VIAL\s+(\d+[.,]\d+)\s+\d+[.,]\d+%\s+\$?[\d.,]+$""",
            RegexOption.IGNORE_CASE
        )
    }
}
