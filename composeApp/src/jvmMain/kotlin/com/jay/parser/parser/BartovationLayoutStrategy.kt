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
                (text.contains("P.O.#") || text.contains("PO#") || Regex("""PL[O0]?\d+""").containsMatchIn(text))
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines)
        var score = 0
        if (text.contains("BARTOVATIONLLC")) score += 100
        if (text.contains("ASTORIA,NY11103")) score += 60
        if (text.contains("INVENTORYRECEIVING")) score += 40
        if (text.contains("PART#TITLEQTYUNITPRICETOTAL")) score += 50
        if (text.contains("UPSGROUND-BILLRECEIVER")) score += 25
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
        val joined = lines.joinToString(" ")
        val direct = Regex("""P\.?O\.?\s*#?\s*(PL[O0]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)

        val fallback = Regex("""\b(PL[O0]?\d+(?:\.\d+)?)\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)

        return (direct ?: fallback)
            ?.uppercase()
            ?.replace("PLO", "PL0")
            ?.substringBefore(".")
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
            ?.groupValues
            ?.get(1)
            ?: if (normalize(lines).contains("ASTORIA,NY11103")) "11103" else null
    }

    private fun parseShipVia(lines: List<String>): String? {
        val compactText = normalize(lines)

        if (!compactText.contains("UPSGROUND-BILLRECEIVER")) {
            return null
        }

        val acct = Regex("""UPSACCOUNT#([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(compactText)
            ?.groupValues
            ?.getOrNull(1)

        return if (acct != null) {
            "UPS Ground - Bill Receiver UPS Account # $acct"
        } else {
            "UPS Ground - Bill Receiver"
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        lines.forEachIndexed { index, line ->
            if (!looksLikeItemRow(line)) return@forEachIndexed

            parseItemRow(
                line = line,
                previousDescription = collectPreviousDescription(lines, index)
            )?.let { items.add(it) }
        }

        return items
    }

    private fun looksLikeItemRow(line: String): Boolean {
        val normalizedLine = line.replace(Regex("""\s+"""), " ").trim()
        val firstToken = normalizedLine.substringBefore(" ")

        if (firstToken.isBlank() || firstToken.all { it.isDigit() }) {
            return false
        }

        return Regex("""\$\d""").containsMatchIn(normalizedLine) &&
                Regex("""\s\d+(?:\.\d+)?\s+\$[\d,]+\.\d{2}\s+\$[\d,]+\.\d{2}\s*$""")
                    .containsMatchIn(normalizedLine)
    }

    private fun parseItemRow(
        line: String,
        previousDescription: String?
    ): ParsedPdfItem? {
        val normalizedLine = line.replace(Regex("""\s+"""), " ").trim()

        val rawSku = Regex("""^[A-Z0-9]+(?:-[A-Z0-9]+)*""", RegexOption.IGNORE_CASE)
            .find(normalizedLine)
            ?.value
            ?: return null

        val qtyMatch = Regex("""\s(\d+(?:\.\d+)?)\s+\$([\d,]+\.\d{2})\s+\$([\d,]+\.\d{2})\s*$""")
            .find(normalizedLine)
            ?: return null

        val qty = qtyMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val unitPrice = qtyMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null

        val rowDescription = normalizedLine
            .removePrefix(rawSku)
            .substringBefore(qtyMatch.groupValues[0])
            .trim()

        val combinedDescription = listOfNotNull(
            previousDescription?.takeIf { it.isNotBlank() },
            rowDescription.takeIf { it.isNotBlank() }
        ).joinToString(" ").trim()

        val sku = normalizeBartovationSku(rawSku, combinedDescription)
        val mappedDescription = ItemMapper.getItemDescription(sku)

        val description = when {
            mappedDescription.isNotBlank() -> mappedDescription
            combinedDescription.isNotBlank() -> combinedDescription
            else -> sku
        }

        return item(
            sku = sku,
            description = description,
            quantity = qty,
            unitPrice = unitPrice
        )
    }

    private fun collectPreviousDescription(lines: List<String>, itemIndex: Int): String? {
        val parts = mutableListOf<String>()

        for (i in itemIndex - 1 downTo maxOf(0, itemIndex - 3)) {
            val candidate = lines[i].replace(Regex("""\s+"""), " ").trim()
            if (candidate.isBlank()) break
            if (isNonItemContextLine(candidate)) break
            if (looksLikeItemRow(candidate)) break
            if (Regex("""\$\d""").containsMatchIn(candidate)) break

            parts.add(candidate)
        }

        return parts
            .asReversed()
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun isNonItemContextLine(line: String): Boolean {
        val compact = normalize(line)

        return compact == "PURCHASEORDER" ||
                compact.startsWith("DATE") ||
                compact.startsWith("BARTOVATIONLLC") ||
                compact.startsWith("248347THST") ||
                compact.startsWith("ASTORIA") ||
                compact.startsWith("PHONE") ||
                compact.contains("ORDERS@BARTOVATION") ||
                compact == "VENDORSHIPTO" ||
                compact == "SHIPVIASHIPPINGTERMS" ||
                compact.contains("UPSGROUND-BILLRECEIVER") ||
                compact == "PART#TITLEQTYUNITPRICETOTAL" ||
                compact == "ORDERNOTES:TAX$0.00" ||
                compact.startsWith("USEUNBRANDEDPACKINGTAPE") ||
                compact.startsWith("SHIPPING") ||
                compact.startsWith("TOTAL")
    }

    private fun normalizeBartovationSku(rawSku: String, description: String): String {
        val sku = rawSku.uppercase().trim()

        /*
         * Some OCR runs split "100 strips" into the end of the SKU:
         * FC-CHLD-PH0245-1V-10s0trips] -> FC-CHLD-PH0245-1V-100.
         */
        if (
            sku.startsWith("FC-CHLD-PH0245-1V-10") &&
            (
                    normalize(description).contains("VIALOF100") ||
                            sku.contains("S0TRIPS") ||
                            sku.contains("STRIPS")
                    )
        ) {
            return "FC-CHLD-PH0245-1V-100"
        }

        return sku
    }

    private fun normalize(lines: List<String>): String =
        lines.joinToString("\n") { normalize(it) }

    private fun normalize(line: String): String =
        line.uppercase().replace(Regex("""\s+"""), "")
}
