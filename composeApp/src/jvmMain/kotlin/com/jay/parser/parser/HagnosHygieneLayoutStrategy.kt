package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.abs

class HagnosHygieneLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("HAGNOSHYGIENECO") &&
                text.contains("HAGNOSHYGIENEGMAILCOM") &&
                text.contains("PURCHASEORDERNO") &&
                text.contains("NANZIHDISTKAOHSIUNGCITY")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("HAGNOSHYGIENECO")) score += 240
        if (text.contains("HAGNOSHYGIENEGMAILCOM")) score += 220
        if (text.contains("NANZIHDISTKAOHSIUNGCITY")) score += 180
        if (text.contains("PURCHASEORDERNO")) score += 140
        if (text.contains("QAC4001V100")) score += 100
        if (text.contains("PAA10001V100")) score += 100
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::normalizeLine)
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
            },
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo {
        val attention = lines.firstNotNullOfOrNull { line ->
            ATTENTION_PATTERN.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }

        val addressLine1 = lines.firstNotNullOfOrNull { line ->
            extractStreetAddress(line, requireRepeatedAddress = true)
        } ?: lines
            .dropWhile { line -> !compact(line).contains("BILLTOSHIPTO") }
            .firstNotNullOfOrNull { line -> extractStreetAddress(line) }

        val cityStateZip = lines.firstNotNullOfOrNull { line ->
            TAIWAN_CITY_PATTERN.findAll(line).lastOrNull()
        }

        val district = cityStateZip
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val city = cityStateZip
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val zip = cityStateZip
            ?.groupValues
            ?.getOrNull(3)
            ?.trim()
            ?.takeIf(String::isNotBlank)

        return ShipTo(
            customer = listOfNotNull(
                "Hagnos Hygiene Co., Ltd.",
                attention?.let { "ATTN: $it" }
            ).joinToString(" - "),
            addressLine1 = addressLine1,
            addressLine2 = district,
            city = city,
            state = "Taiwan",
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.find(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[3]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[4]) ?: return@mapNotNull null
        val extension = parseNumber(match.groupValues[5]) ?: return@mapNotNull null
        if (quantity <= 0.0 || unitPrice <= 0.0 || extension <= 0.0) return@mapNotNull null
        if (abs(quantity * unitPrice - extension) > EXTENSION_TOLERANCE) return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { match.groupValues[2].trim() },
            quantity = quantity,
            unitPrice = unitPrice,
            uom = "EA"
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun extractStreetAddress(
        line: String,
        requireRepeatedAddress: Boolean = false
    ): String? {
        val streetStarts = STREET_START_PATTERN.findAll(line).toList()
        if (streetStarts.isEmpty() || requireRepeatedAddress && streetStarts.size < 2) return null

        val firstStart = streetStarts.first().range.first
        val secondStart = streetStarts.getOrNull(1)?.range?.first ?: line.length
        return line.substring(firstStart, secondStart)
            .replace(Regex(""",?\s*\d{1,2}/\d{1,2}/\d{4}\s*$"""), "")
            .trim()
            .trimEnd(',')
            .takeIf(String::isNotBlank)
    }

    private fun normalizeLine(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val customer: String,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String,
        val zip: String?
    )

    private companion object {
        const val CUSTOMER_ID = "HAGNOS HYGIENE"
        const val EXTENSION_TOLERANCE = 0.02

        val ORDER_NUMBER_PATTERN = Regex("""\bH\d{10}\b""", RegexOption.IGNORE_CASE)
        val ATTENTION_PATTERN = Regex(
            """\bATTN:\s*(?:ATTN:\s*)?(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val STREET_START_PATTERN = Regex("""\bNo\.\s*\d+\s*,""", RegexOption.IGNORE_CASE)
        val TAIWAN_CITY_PATTERN = Regex(
            """([A-Za-z][A-Za-z .'-]+\s+Dist\.)\s*,\s*([A-Za-z][A-Za-z .'-]+?)\s+(\d{5,6})\s*,\s*Taiwan""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+US\$\s*([\d,]+\.\d{2,3})\s+US\$\s*([\d,]+\.\d{2,3})\s*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
