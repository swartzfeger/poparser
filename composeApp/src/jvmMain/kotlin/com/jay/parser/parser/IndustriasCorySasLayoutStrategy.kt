package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class IndustriasCorySasLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "INDUSTRIAS CORY SAS"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("INDUSTRIASCORYS.A.S") ||
                text.contains("IVONNEESTIVARIZ") ||
                text.contains("DELIVERYADRESSCALLE9CSUR") ||
                text.contains("PURCHASEORDERNO.IM-")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("INDUSTRIASCORYS.A.S")) score += 120
        if (text.contains("IVONNEESTIVARIZ")) score += 70
        if (text.contains("DELIVERYADRESSCALLE9CSUR")) score += 80
        if (text.contains("MEDELLÍN-COLOMBIA") || text.contains("MEDELLIN-COLOMBIA")) score += 60
        if (text.contains("PURCHASEORDERNO.IM-")) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "INDUSTRIAS CORY SAS",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""PURCHASE\s+ORDER\s+NO\.\s*(IM-\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.uppercase()
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("CREDIT CARD", ignoreCase = true) -> "Credit Card"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val line = lines.firstOrNull { it.contains("Delivery adress", ignoreCase = true) }
            ?: return ShipToBlock(
                shipToCustomer = "INDUSTRIAS CORY S.A.S",
                addressLine1 = "CALLE 9C SUR # 50FF – 146",
                city = "Medellín",
                state = "Colombia",
                zip = null
            )

        val addressText = line.substringAfter("Delivery adress", "").trim()

        return ShipToBlock(
            shipToCustomer = "INDUSTRIAS CORY S.A.S",
            addressLine1 = prettifyAddress(addressText),
            city = "Medellín",
            state = "Colombia",
            zip = null
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()

            val match = Regex(
                """^([A-Z0-9-]+)\s+(\d+)\s+\$([\d,]+(?:\.\d+)?|\d+,\d+)\s+\$([\d,]+(?:\.\d+)?|\d+,\d+)$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val sku = match.groupValues[1].trim().uppercase()
            val quantity = match.groupValues[2].toDoubleOrNull() ?: continue
            val unitPrice = parseMoney(match.groupValues[3]) ?: continue

            val descriptionParts = mutableListOf<String>()

            val prev2 = lines.getOrNull(i - 2)?.trim().orEmpty()
            val prev1 = lines.getOrNull(i - 1)?.trim().orEmpty()
            val next1 = lines.getOrNull(i + 1)?.trim().orEmpty()

            if (prev2.isNotBlank() && !looksLikeNoise(prev2)) descriptionParts.add(prev2)
            if (prev1.isNotBlank() && !looksLikeNoise(prev1)) descriptionParts.add(prev1)
            if (next1.isNotBlank() && !looksLikeNoise(next1)) descriptionParts.add(next1)

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                descriptionParts.joinToString(" ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            }

            val key = "$sku|$quantity|$unitPrice"
            if (seen.add(key)) {
                items.add(
                    item(
                        sku = sku,
                        description = description,
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

        return items
    }

    private fun parseMoney(value: String): Double? {
        val cleaned = value.trim()
            .replace(".", "")
            .replace(",", ".")
        return cleaned.toDoubleOrNull()
    }

    private fun looksLikeNoise(line: String): Boolean {
        val text = normalize(line)
        return text.startsWith("REFERENCEPRODUCTNAMEQUANTITYPRICEAMOUNT") ||
                text.startsWith("SUBTOTAL") ||
                text.startsWith("TAX") ||
                text.startsWith("TOTAL") ||
                text == "\$0,00" ||
                text.contains("INDUSTRIASCORYS.A.SCALLE10SUR") ||
                Regex("""^[A-Z0-9-]+\s+\d+\s+\$""").containsMatchIn(line)
    }

    private fun prettifyAddress(value: String): String {
        return value
            .replace("–", "-")
            .replace("Medellín, Colombia", "")
            .replace("Medellin, Colombia", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd(',', '-')
            .trim()
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}