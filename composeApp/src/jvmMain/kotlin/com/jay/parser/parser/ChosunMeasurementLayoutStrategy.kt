package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ChosunMeasurementLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "CHOSUN MEASUREMENT"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("CHOSUNMEASUREMENT") ||
                text.contains("CHOSUNSHOP.CO.KR") ||
                text.contains("SIGNATUREOFCHOSUNMEASUREMENT") ||
                text.contains("JOOMINKOH")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("CHOSUNMEASUREMENT")) score += 180
        if (text.contains("CHOSUNSHOP.CO.KR")) score += 100
        if (text.contains("SIGNATUREOFCHOSUNMEASUREMENT")) score += 100
        if (text.contains("SEOUL,KOREA")) score += 70
        if (text.contains("JOOMINKOH")) score += 50
        if (text.contains("PURCHASEORDER")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map {
            it.replace(Regex("""\s+"""), " ").trim()
        }

        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "CHOSUN MEASUREMENT",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        val match = Regex("""Date:\s*(\d{4})\.(\d{2})\.(\d{2})""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?: Regex("""\b(\d{4})\.(\d{2})\.(\d{2})\b""")
                .find(joined)
            ?: return null

        return "PO${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}"
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val addressLine = lines.firstOrNull {
            it.contains("Muhakbong", ignoreCase = true) &&
                    it.contains("Seoul", ignoreCase = true) &&
                    it.contains("Korea", ignoreCase = true)
        }

        val cleaned = addressLine
            ?.substringBefore(", Tel:", addressLine)
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?: "2nd Floor 20, Muhakbong 15-gil, Seongdong-gu, Seoul, Korea, Zip Code 04710"

        return ShipToBlock(
            shipToCustomer = "CHOSUN MEASUREMENT",
            addressLine1 = "2nd Floor 20, Muhakbong 15-gil",
            addressLine2 = "Seongdong-gu",
            city = "Seoul",
            state = "Korea",
            zip = Regex("""Zip Code\s*(\d{5})""", RegexOption.IGNORE_CASE)
                .find(cleaned)
                ?.groupValues
                ?.get(1)
                ?: "04710"
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val cleanLine = line
                .replace(Regex("""\s+"""), " ")
                .trim()

            val match = Regex(
                """^(?:\d+\s+)?([A-Z0-9]+(?:-[A-Z0-9]+)+|LABELS\s+[A-Z0-9-]+)\s+(\d+(?:\.\d+)?)$""",
                RegexOption.IGNORE_CASE
            ).find(cleanLine) ?: continue

            val sku = normalizeChosunSku(match.groupValues[1])
            val quantity = match.groupValues[2].toDoubleOrNull() ?: continue

            val key = "$sku|$quantity"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = null
                )
            )
        }

        return items
    }

    private fun normalizeChosunSku(raw: String): String {
        return raw
            .trim()
            .uppercase()
            .replace(Regex("""\s+"""), " ")
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}