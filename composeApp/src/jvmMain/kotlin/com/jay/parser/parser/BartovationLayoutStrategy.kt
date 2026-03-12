package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class BartovationLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Bartovation"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines)
        return text.contains("BARTOVATIONLLC") &&
                text.contains("ASTORIA,NY11103") &&
                (text.contains("PL022326") || text.contains("P.O.#") || text.contains("PO#"))
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines)
        var score = 0
        if (text.contains("BARTOVATIONLLC")) score += 100
        if (text.contains("ASTORIA,NY11103")) score += 60
        if (text.contains("INVENTORYRECEIVING")) score += 40
        if (text.contains("PART#TITLEQTYUNITPRICETOTAL")) score += 50
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)

        return ParsedPdfFields(
            customerName = parseShipToName(clean),
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = parseShipToName(clean),
            addressLine1 = parseShipToAddress1(clean),
            addressLine2 = parseShipToAddress2(clean),
            city = parseCity(clean),
            state = parseState(clean),
            zip = parseZip(clean),
            terms = parseShipVia(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val line = lines.firstOrNull { it.uppercase().contains("P.O.#") || it.uppercase().contains("PO#") }
            ?: return null
        return Regex("""PL\d+""", RegexOption.IGNORE_CASE).find(line)?.value
    }

    private fun parseShipToName(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it) == "BARTOVATIONLLC" }
            ?.let { "BARTOVATION LLC" }
    }

    private fun parseShipToAddress1(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it).contains("248547THST") }
            ?.let { "2485 47TH ST" }
    }

    private fun parseShipToAddress2(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it).contains("INVENTORYRECEIVING") }
            ?.let { "INVENTORY RECEIVING" }
    }

    private fun parseCity(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it).contains("ASTORIA,NY11103") }?.let { "ASTORIA" }
    }

    private fun parseState(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it).contains("ASTORIA,NY11103") }?.let { "NY" }
    }

    private fun parseZip(lines: List<String>): String? {
        return Regex("""ASTORIA,?\s*NY\s*(\d{5})""", RegexOption.IGNORE_CASE)
            .find(lines.joinToString(" "))
            ?.groupValues?.get(1)
            ?: if (normalize(lines).contains("ASTORIA,NY11103")) "11103" else null
    }

    private fun parseShipVia(lines: List<String>): String? {
        val line = lines.firstOrNull { normalize(it).contains("UPSGROUND-BILLRECEIVER") } ?: return null
        val acct = Regex("""UPSACCOUNT#([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(normalize(line))
            ?.groupValues?.get(1)
        return if (acct != null) "UPS Ground - Bill Receiver UPS Account # $acct" else "UPS Ground - Bill Receiver"
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val itemLines = lines.filter { looksLikeItemRow(it) }

        return itemLines.mapNotNull { parseItemRow(it) }
    }

    private fun looksLikeItemRow(line: String): Boolean {
        val upper = line.uppercase()
        return Regex("""^[A-Z0-9\-]+""").containsMatchIn(upper) &&
                upper.contains("$") &&
                Regex("""\$\d""").containsMatchIn(upper)
    }

    private fun parseItemRow(line: String): ParsedPdfItem? {
        val normalizedLine = line.replace(Regex("""\s+"""), " ").trim()

        val sku = Regex("""^[A-Z0-9\-]+""").find(normalizedLine)?.value ?: return null

        val qtyMatch = Regex("""\s(\d+(?:\.\d+)?)\s+\$([\d,]+\.\d{2})\s+\$([\d,]+\.\d{2})\s*$""")
            .find(normalizedLine) ?: return null

        val qty = qtyMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val unitPrice = qtyMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null

        val descPart = normalizedLine
            .removePrefix(sku)
            .substringBefore(qtyMatch.groupValues[0])
            .trim()

        val description = ItemMapper.getItemDescription(sku).takeIf { it.isNotBlank() } ?: descPart

        return item(
            sku = sku,
            description = description,
            quantity = qty,
            unitPrice = unitPrice
        )
    }

    private fun normalize(lines: List<String>): String =
        lines.joinToString("\n") { normalize(it) }

    private fun normalize(line: String): String =
        line.uppercase().replace(Regex("""\s+"""), "")
}