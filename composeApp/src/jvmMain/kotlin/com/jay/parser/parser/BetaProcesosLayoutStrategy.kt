package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class BetaProcesosLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Beta Procesos"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("BETACOMMERCIALLLC") &&
                text.contains("M&GFORWARDINGLLC") &&
                text.contains("PROVIDERIDPR01563")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("BETACOMMERCIALLLC")) score += 120
        if (text.contains("M&GFORWARDINGLLC")) score += 100
        if (text.contains("PROVIDERIDPR01563")) score += 80
        if (text.contains("SOLDTOSHIPTO")) score += 50
        if (text.contains("PURCHASEORDER")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "BETA PROCESOS",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? = lines
        .firstNotNullOfOrNull { line ->
            PO_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
        }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val hasShipTo = lines.any { compact(it).contains("M&GFORWARDINGLLC") }
        if (!hasShipTo) return ShipToBlock(null, null, null, null, null)

        val addressLine = lines.firstOrNull { STREET_PATTERN.containsMatchIn(it) }
        val addressLine1 = addressLine
            ?.let { STREET_PATTERN.find(it)?.groupValues?.get(1) }
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()

        val cityLine = lines.firstOrNull { CITY_STATE_ZIP_PATTERN.containsMatchIn(it) }
        val cityMatch = cityLine?.let(CITY_STATE_ZIP_PATTERN::find)

        return ShipToBlock(
            customer = "M&G FORWARDING LLC",
            addressLine1 = addressLine1,
            city = cityMatch?.groupValues?.get(1)?.trim(),
            state = cityMatch?.groupValues?.get(2)?.let { rawState ->
                if (rawState.equals("Texas", ignoreCase = true)) "TX" else rawState.uppercase()
            },
            zip = cityMatch?.groupValues?.get(3)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        val seen = mutableSetOf<String>()

        lines.forEach { line ->
            val sku = SKU_PATTERN.find(line)?.value?.uppercase() ?: return@forEach
            val amounts = ITEM_AMOUNTS_PATTERN.find(line) ?: return@forEach
            val quantity = amounts.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
            val unitPrice = amounts.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@forEach
            if (!seen.add("$sku|$quantity|$unitPrice")) return@forEach

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku),
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun cleanLine(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9&]"""), "")

    private data class ShipToBlock(
        val customer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private companion object {
        val PO_NUMBER_PATTERN = Regex("""\bPO\s*#\s*(\d{4,10})\b""", RegexOption.IGNORE_CASE)
        val STREET_PATTERN = Regex("""\b(9320\s+San\s+Mateo)\b""", RegexOption.IGNORE_CASE)
        val CITY_STATE_ZIP_PATTERN = Regex(
            """\b([A-Za-z .'-]+?)\s+(Texas|TX)\s*(\d{5}(?:-\d{4})?)\b""",
            RegexOption.IGNORE_CASE
        )
        val SKU_PATTERN = Regex("""(?:QAC|CHL)-\d+(?:-[A-Z0-9]+){2,}""", RegexOption.IGNORE_CASE)
        val ITEM_AMOUNTS_PATTERN = Regex(
            """\s+([\d,]+(?:\.\d+)?)\s+([\d,]+\.\d{3})\s+([\d,]+\.\d{2})$"""
        )
    }
}
