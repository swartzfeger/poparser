package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.abs
import kotlin.math.round

class ScinoPharmTaiwanLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        val hasScinoIdentity = text.contains("SCINOPHARMT") || text.contains("NANKE8THROAD")

        return hasScinoIdentity &&
                text.contains("203792PRECISIONLABORATORIES") &&
                text.contains("PURCHASEORDER") &&
                text.contains(MATERIAL_NUMBER)
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SCINOPHARMT")) score += 240
        if (text.contains("203792PRECISIONLABORATORIES")) score += 220
        if (text.contains("NANKE8THROAD")) score += 200
        if (text.contains("SHANHUATAINAN")) score += 180
        if (text.contains("AMMONIALEAKDETECTIONCLOTH")) score += 160
        if (text.contains(MATERIAL_NUMBER)) score += 140
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "ScinoPharm Taiwan, Ltd.",
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = shipTo?.addressLine2,
            city = shipTo?.city,
            state = shipTo?.state,
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val joined = lines.joinToString(" ")
        val shipToStart = SHIP_TO_START_PATTERN.find(joined)?.range?.last?.plus(1) ?: return null
        val freightStart = FREIGHT_TERMS_PATTERN.find(joined, shipToStart)?.range?.first ?: joined.length
        val shipToBlock = joined.substring(shipToStart, freightStart)
        val address = ADDRESS_PATTERN.find(shipToBlock) ?: return null
        val sciencePark = address.groupValues[1]
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf(String::isNotBlank)

        return ShipTo(
            addressLine1 = "No.1, Nan-Ke 8th Road",
            addressLine2 = listOfNotNull(sciencePark, "Taiwan").joinToString(", "),
            city = "Shan-Hua",
            state = "Tainan",
            zip = address.groupValues[2]
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { index, line ->
            if (!MATERIAL_NUMBER_PATTERN.containsMatchIn(line)) return@forEachIndexed

            val itemText = (index..minOf(index + 2, lines.lastIndex))
                .joinToString(" ") { candidateIndex -> lines[candidateIndex] }
            val monetaryValues = MONEY_PATTERN.findAll(itemText)
                .mapNotNull { match -> parseNumber(match.value) }
                .toList()
            if (monetaryValues.size < 2) return@forEachIndexed

            val unitPrice = monetaryValues[monetaryValues.lastIndex - 1]
            val extension = monetaryValues.last()
            if (unitPrice <= 0.0 || extension <= 0.0) return@forEachIndexed

            val calculatedQuantity = extension / unitPrice
            val quantity = round(calculatedQuantity)
            if (quantity <= 0.0 || abs(calculatedQuantity - quantity) > QUANTITY_TOLERANCE) {
                return@forEachIndexed
            }

            add(
                item(
                    sku = SKU,
                    description = ItemMapper.getItemDescription(SKU).ifBlank { SKU },
                    quantity = quantity,
                    unitPrice = unitPrice,
                    uom = "EA"
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

    private data class ShipTo(
        val addressLine1: String,
        val addressLine2: String,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "SCINO PHARM TAIWAN L"
        const val MATERIAL_NUMBER = "710945"
        const val SKU = "505-1-2020"
        const val QUANTITY_TOLERANCE = 0.05

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDER\s*NO\b[^\d]*(\d{10})\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_START_PATTERN = Regex(
            """SHIP\s*TO\s*:?[\s|]*SCINOPHARM\s+TAIWAN,?\s+LTD\.?""",
            RegexOption.IGNORE_CASE
        )
        val FREIGHT_TERMS_PATTERN = Regex("""FREIGHT\s+TERMS""", RegexOption.IGNORE_CASE)
        val ADDRESS_PATTERN = Regex(
            """NO\.?\s*1,\s*NAN-?KE\s+8TH\s+ROAD,\s*(?:(SOUTHERN\s+TAIWAN\s+SCIENCE\s+PARK),\s*)?SHAN-?HUA,\s*TAINAN\s+(\d{5,6}),\s*TAIWAN""",
            RegexOption.IGNORE_CASE
        )
        val MATERIAL_NUMBER_PATTERN = Regex("""\b710945\b""")
        val MONEY_PATTERN = Regex("""\b\d[\d,]*\.\d{2}\b""")
    }
}
