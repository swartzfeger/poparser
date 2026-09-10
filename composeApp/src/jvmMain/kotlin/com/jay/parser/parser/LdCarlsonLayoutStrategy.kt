package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class LdCarlsonLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "LD CARLSON"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString(" "))
        return (text.contains("DCARLSON") || text.contains("LDCARISON")) &&
            (text.contains("WINEBEERMAKINGSUPPLIES") ||
                text.contains("463PORTAGE") ||
                text.contains("212128180"))
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString(" "))
        var score = 0

        if (text.contains("DCARLSON") || text.contains("LDCARISON")) score += 140
        if (text.contains("WINEBEERMAKINGSUPPLIES")) score += 90
        if (text.contains("463PORTAGE")) score += 70
        if (text.contains("212128180")) score += 60
        if (text.contains("PONUMBER")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipToLocation = parseShipToCityStateZip(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = parseShipToCustomer(clean),
            addressLine1 = "463 Portage Blvd.",
            addressLine2 = null,
            city = shipToLocation.city,
            state = shipToLocation.state,
            zip = shipToLocation.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        return lines.asSequence()
            .mapNotNull { ORDER_NUMBER_PATTERN.find(it)?.groupValues?.getOrNull(1) }
            .firstOrNull()
    }

    private fun parseShipToCustomer(lines: List<String>): String {
        val joined = lines.joinToString(" ")
        return SHIP_TO_NAME_PATTERN.find(joined)?.groupValues?.getOrNull(1)?.trim()
            ?: "LD Carlson Company"
    }

    private fun parseShipToCityStateZip(lines: List<String>): CityStateZip {
        val joined = lines.joinToString(" ")
        val match = SHIP_TO_CITY_PATTERN.find(joined)
        return CityStateZip(
            city = "Kent",
            state = match?.groupValues?.get(1)?.uppercase() ?: "OH",
            zip = match?.groupValues?.get(2) ?: "44240"
        )
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return if (TERMS_PATTERN.containsMatchIn(joined)) "1% 10 Days, Net 30" else null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val knownSkusByCompact = ItemMapper.getAllSkus()
            .associateBy(::compact)

        val skus = lines.mapNotNull { line ->
            val compactLine = compact(line)
            val directMatch = knownSkusByCompact.entries
                .filter { (compactSku, _) -> compactSku.length >= MIN_SKU_LENGTH && compactLine.contains(compactSku) }
                .maxByOrNull { (compactSku, _) -> compactSku.length }
                ?.value

            directMatch ?: SKU_PATTERN.find(normalizeDashes(line))
                ?.groupValues
                ?.getOrNull(1)
                ?.uppercase()
        }

        val quantities = lines.flatMap { line ->
            QUANTITY_PATTERN.findAll(line)
                .mapNotNull { match ->
                    match.groupValues[1]
                        .replace('O', '0')
                        .replace('o', '0')
                        .toDoubleOrNull()
                }
                .toList()
        }

        return skus.zip(quantities).map { (sku, quantity) ->
            item(
                sku = sku,
                description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                quantity = quantity,
                unitPrice = null,
                uom = "EA"
            )
        }
    }

    private fun normalizeDashes(value: String): String {
        return value
            .uppercase()
            .replace(Regex("""\s*[-–—]\s*"""), "-")
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_NAME = "LD CARLSON"
        const val MIN_SKU_LENGTH = 8

        val ORDER_NUMBER_PATTERN = Regex(
            """\bPO\s*(?:NUMBER|NUM8ER|NO\.?)?\s*[:#]?\s*(\d{6})\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_NAME_PATTERN = Regex(
            """SHIP\s*TO\s+(LD\s+CARLSON\s+COMPANY)""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_CITY_PATTERN = Regex(
            """\bKENT\s+([A-Z]{2})\s+(44240)\b""",
            RegexOption.IGNORE_CASE
        )
        val TERMS_PATTERN = Regex(
            """1\s*%\s*10\s+DAYS\s*,?\s*NET\s*30""",
            RegexOption.IGNORE_CASE
        )
        val SKU_PATTERN = Regex(
            """\b([A-Z0-9]{2,}(?:-[A-Z0-9]+){2,})\b""",
            RegexOption.IGNORE_CASE
        )
        val QUANTITY_PATTERN = Regex(
            """\b(\d{1,6})[.,][0O]{3}\s+(?:EA|EBA|HA|BA|8A)\b""",
            RegexOption.IGNORE_CASE
        )
    }
}
