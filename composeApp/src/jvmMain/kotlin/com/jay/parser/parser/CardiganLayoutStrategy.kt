package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.abs

class CardiganLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "CARDIGAN"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString(" "))
        return text.contains("CARDIGANGROUP") &&
            text.contains("BLVDRAFAELLANDIVAR") &&
            text.contains("GUATEMALA01016")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString(" "))
        var score = 0

        if (text.contains("CARDIGANGROUP")) score += 150
        if (text.contains("BLVDRAFAELLANDIVAR")) score += 90
        if (text.contains("GUATEMALA01016")) score += 70
        if (text.contains("CPSINTERNATIONAL")) score += 50
        if (text.contains("PONO")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "CPS INTERNATIONAL",
            addressLine1 = "2200 NW 129TH AVE SUITE 108",
            addressLine2 = parseDeliveryReference(clean),
            city = "MIAMI",
            state = "FL",
            zip = "33182-2489",
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return ORDER_NUMBER_PATTERN.find(joined)?.groupValues?.getOrNull(1)
    }

    private fun parseDeliveryReference(lines: List<String>): String? {
        return lines.firstOrNull { line ->
            val normalized = compact(line)
            normalized.contains("TECHNICAL") && normalized.contains("GT")
        }?.replace(Regex("""\s+"""), " ")
            ?.replace(Regex("""IBAGARI\s*LE""", RegexOption.IGNORE_CASE), "IBAGARI LE")
            ?.replace(Regex("""\s*-\s*"""), " - ")
            ?.trim()
    }

    private fun parseTerms(lines: List<String>): String? {
        val text = compact(lines.joinToString(" "))
        return if (text.contains("METHODOFPAYMENTPREPAID")) "Prepaid" else null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        return lines.mapNotNull { line ->
            val match = ITEM_ROW_PATTERN.find(line.trim()) ?: return@mapNotNull null
            val rawSku = match.groupValues[1].uppercase()
            if (rawSku in NON_PRODUCT_SKUS) return@mapNotNull null

            val sku = normalizeCardiganSku(rawSku)
            val description = ItemMapper.getItemDescription(sku)
            if (description.isBlank()) return@mapNotNull null

            val quantity = match.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
            val unitPrice = match.groupValues[4]
                .replace(",", "")
                .toDoubleOrNull()
                ?: return@mapNotNull null

            item(
                sku = sku,
                description = description,
                quantity = quantity,
                unitPrice = unitPrice,
                uom = match.groupValues[2].uppercase()
            )
        }
    }

    private fun normalizeCardiganSku(rawSku: String): String {
        val rawCompact = compact(rawSku)
        val knownSkus = ItemMapper.getAllSkus()

        knownSkus.singleOrNull { compact(it) == rawCompact }?.let { return it }

        return knownSkus.singleOrNull { candidate ->
            differsByAtMostOneCharacter(rawCompact, compact(candidate))
        } ?: rawSku
    }

    private fun differsByAtMostOneCharacter(first: String, second: String): Boolean {
        if (abs(first.length - second.length) > 1) return false

        val shorter = if (first.length <= second.length) first else second
        val longer = if (first.length <= second.length) second else first
        var shortIndex = 0
        var longIndex = 0
        var differences = 0

        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else {
                differences++
                if (differences > 1) return false

                if (shorter.length == longer.length) shortIndex++
                longIndex++
            }
        }

        if (longIndex < longer.length) differences++
        return differences <= 1
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }

    private companion object {
        const val CUSTOMER_NAME = "CARDIGAN"

        val NON_PRODUCT_SKUS = setOf("CCFEE", "FREIGHT")
        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*NO\.?\s*:\s*(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+([A-Z0-9-]+)(?:\s+.*?)?\s+(VIAL|UNIT)\s+(\d+(?:\.\d+)?)\s+\$([\d,.]+)\s+\$[\d,.]+$""",
            RegexOption.IGNORE_CASE
        )
    }
}
