package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CharlotteProductsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "CHARLOTTE PRODUCTS"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("CHARLOTTEPRODUCTSLTD") ||
                text.contains("CHARLOTTEPRODUCTS") ||
                text.contains("HSTNUMBER105105217RT0001") ||
                text.contains("PLEASESENDCONFIRMATIONTHISPOHASBEENRECEIVED")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("CHARLOTTEPRODUCTSLTD")) score += 180
        if (text.contains("CHARLOTTEPRODUCTS")) score += 140
        if (text.contains("HSTNUMBER105105217RT0001")) score += 100
        if (text.contains("PETERBOROUGH")) score += 70
        if (text.contains("PRELAB")) score += 50
        if (text.contains("PLEASESENDCONFIRMATIONTHISPOHASBEENRECEIVED")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map {
            it.replace(Regex("""\s+"""), " ").trim()
        }

        val shipTo = parseShipTo()

        return ParsedPdfFields(
            customerName = "CHARLOTTE PRODUCTS",
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

        Regex("""\bPRELAB\s+(\d{5,})\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(\d{5,})\s*$""", RegexOption.IGNORE_CASE)
            .find(lines.firstOrNull { it.contains("FISHER", ignoreCase = true) && it.contains("PETERBOROUGH", ignoreCase = true) }.orEmpty())
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return Regex("""\b(46\d{3}|47\d{3})\b""")
            .find(joined)
            ?.groupValues
            ?.get(1)
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("NET 30 (A/P)", ignoreCase = true) -> "Net 30"
            joined.contains("NET 30", ignoreCase = true) -> "Net 30"
            else -> null
        }
    }

    private fun parseShipTo(): ShipToBlock {
        return ShipToBlock(
            shipToCustomer = "CHARLOTTE PRODUCTS LTD.",
            addressLine1 = "2060 FISHER DRIVE",
            city = "PETERBOROUGH",
            state = "CANADA",
            zip = "K9J 6X6"
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            val previous = lines.getOrNull(i - 1)?.trim().orEmpty()
            val combined = "$previous $line".replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^(?:[A-Z0-9]+\s+)?([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+EA\s+USD\s*([\d.]+)\s+USD\s*([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: Regex(
                """^(?:[A-Z0-9]+\s+)?([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+EA\s+USD\s*([\d.]+)\s+USD\s*([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(combined) ?: continue

            val sku = normalizeCharlotteSku(match.groupValues[1])
            val descriptionFromPo = match.groupValues[2].trim()
            val quantity = match.groupValues[3].toDoubleOrNull() ?: continue
            val unitPrice = match.groupValues[4].toDoubleOrNull() ?: continue

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { descriptionFromPo },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun normalizeCharlotteSku(raw: String): String {
        return raw
            .trim()
            .uppercase()
            .replace(Regex("""\s+"""), "")
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