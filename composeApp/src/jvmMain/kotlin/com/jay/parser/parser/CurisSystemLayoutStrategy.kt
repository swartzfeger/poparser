package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CurisSystemLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "CURIS System"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CURISSYSTEMLLC") &&
                text.contains("610KANECOURT") &&
                text.contains("PURCHASEORDER") &&
                text.contains("PRECISIONLABORATORIES")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("CURISSYSTEMLLC")) score += 200
        if (text.contains("610KANECOURT")) score += 180
        if (text.contains("OVIEDOFL32765")) score += 160
        if (text.contains("PURCHASEORDER")) score += 120
        if (text.contains("PRECISIONLABORATORIES")) score += 100
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
                ORDER_NUMBER_PATTERN.matchEntire(line)?.groupValues?.get(1)
            },
            shipToCustomer = "CURIS System",
            addressLine1 = "610 Kane Court",
            city = "Oviedo",
            state = "FL",
            zip = "32765",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val headerIndex = lines.indexOfFirst { line ->
            compact(line).startsWith("DESCRIPTION") && compact(line).contains("MPN")
        }
        if (headerIndex < 0) return emptyList()

        val quantityBeforeMpn = compact(lines[headerIndex]).startsWith("DESCRIPTIONQTYMPN")
        return lines.drop(headerIndex + 1).mapIndexedNotNull { offset, line ->
            if (line.startsWith("Total", ignoreCase = true)) return@mapIndexedNotNull null

            val match = if (quantityBeforeMpn) {
                QUANTITY_FIRST_ITEM_PATTERN.matchEntire(line)
            } else {
                MPN_FIRST_ITEM_PATTERN.matchEntire(line)
            } ?: return@mapIndexedNotNull null

            val quantityIndex = if (quantityBeforeMpn) 2 else 3
            val skuIndex = if (quantityBeforeMpn) 3 else 2
            val priceIndex = 4
            val description = match.groupValues[1].trim()
            val rawSku = match.groupValues[skuIndex].uppercase()
            val nextLine = lines.getOrNull(headerIndex + offset + 2)
            val sku = resolveWrappedSku(rawSku, description, nextLine)
            val quantity = parseNumber(match.groupValues[quantityIndex]) ?: return@mapIndexedNotNull null
            val unitPrice = parseNumber(match.groupValues[priceIndex]) ?: return@mapIndexedNotNull null

            item(
                sku = sku,
                description = ItemMapper.getItemDescription(sku).ifBlank {
                    description
                },
                quantity = quantity,
                unitPrice = unitPrice
            )
        }
    }

    private fun resolveWrappedSku(rawSku: String, description: String, nextLine: String?): String {
        val statedPackSize = DESCRIPTION_PACK_SIZE_PATTERN
            .find(description)
            ?.groupValues
            ?.get(1)
            ?: return rawSku
        if (rawSku.substringAfterLast('-') == statedPackSize) return rawSku

        val suffix = nextLine
            ?.let { WRAPPED_SKU_SUFFIX_PATTERN.find(it) }
            ?.groupValues
            ?.get(1)
            ?.uppercase()
            ?: return rawSku
        val candidate = rawSku + suffix

        return candidate.takeIf { it.substringAfterLast('-') == statedPackSize } ?: rawSku
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "CURIS SYSTEM"

        val ORDER_NUMBER_PATTERN = Regex("""^\d{1,2}/\d{1,2}/\d{4}\s+(\d+)$""")
        val MPN_FIRST_ITEM_PATTERN = Regex(
            """^(.+?)\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val QUANTITY_FIRST_ITEM_PATTERN = Regex(
            """^(.+?)\s+([\d,]+(?:\.\d+)?)\s+([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val WRAPPED_SKU_SUFFIX_PATTERN = Regex("""(?:^|\s)([A-Z0-9]+)\s*$""")
        val DESCRIPTION_PACK_SIZE_PATTERN = Regex("""(\d+)\s*$""")
    }
}
