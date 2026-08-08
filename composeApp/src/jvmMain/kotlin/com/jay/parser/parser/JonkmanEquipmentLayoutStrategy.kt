package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.round

class JonkmanEquipmentLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Jonkman Equipment"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("JONKMAN") &&
                text.contains("EQUIPMENT") &&
                text.contains("SCHOLTENSEQUIPMENT") &&
                text.contains("8223GUIDEMERIDIANRD") &&
                text.contains("2PREC")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("JONKMANEQUIPMENTLTD")) score += 180
        if (text.contains("SCHOLTENSEQUIPMENT")) score += 160
        if (text.contains("8223GUIDEMERIDIANRD")) score += 140
        if (text.contains("LYNDENWA98264")) score += 120
        if (text.contains("2PREC")) score += 100
        if (text.contains("PARTNUMBERDESCRIPTIONORDEREDUNITPRICEPRICE")) score += 80
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
            shipToCustomer = "Jonkman Equipment Ltd",
            addressLine1 = "c/o Scholtens Equipment",
            addressLine2 = "8223 Guide Meridian Rd",
            city = "Lynden",
            state = "WA",
            zip = "98264",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val tail = ITEM_ROW_TAIL_PATTERN.find(line) ?: return@mapNotNull null
        val prefix = line.substring(0, tail.range.first)
        val rawSku = SKU_PATTERN.find(prefix)?.value ?: return@mapNotNull null
        val sku = normalizeJonkmanSku(rawSku)
        val quantity = parseNumber(tail.groupValues[1]) ?: return@mapNotNull null
        val extension = parseNumber(tail.groupValues[4]) ?: return@mapNotNull null
        if (quantity <= 0.0) return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            // The extension is more reliable than the OCR'd unit-price column;
            // handwritten marks caused 20.00 to be recognized as 20.60 on PO 13018.
            unitPrice = round((extension / quantity) * 100.0) / 100.0
        )
    }

    private fun normalizeJonkmanSku(value: String): String {
        val normalized = value.uppercase()
            .replace("PH0O114", "PH0114")
            .replace("PHO114", "PH0114")

        return when (normalized) {
            "D10-1V-50", "1D10-1V-50" -> "CHL-D10-1V-50"
            else -> normalized
        }
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "JONKMAN EQUIPMENT"

        val ORDER_NUMBER_PATTERN = Regex("""\b(1\d{4})\b""")
        val SKU_PATTERN = Regex("""\b[A-Z0-9]+(?:-[A-Z0-9]+){2,}\b""", RegexOption.IGNORE_CASE)
        val ITEM_ROW_TAIL_PATTERN = Regex(
            """\s+(\d+(?:\.\d+)?)\s+(EA|EACH)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s*[^A-Z0-9]*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
