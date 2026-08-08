package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.round

class SomersetEducationalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Somerset Educational"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("SOMERSETEDUCATIONALPTYLTD") &&
                text.contains("POBOX281") &&
                text.contains("SOMERSETEAST") &&
                text.contains("VAT4120177573")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("SOMERSETEDUCATIONALPTYLTD")) score += 220
        if (text.contains("POBOX281")) score += 190
        if (text.contains("SOMERSETEAST")) score += 170
        if (text.contains("VAT4120177573")) score += 150
        if (text.contains("SSEDC0ZA") || text.contains("SSEDCOZA")) score += 130
        if (text.contains("DOCUMENTNOPO")) score += 110
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
                ORDER_NUMBER_PATTERN.find(line)?.value?.uppercase()
            },
            // The digital forms incorrectly put Precision's own Arizona address
            // in Deliver To. The scanned customer copy supplies the real destination.
            shipToCustomer = "Somerset Educational (Pty) Ltd",
            addressLine1 = "Charles Street",
            city = "Somerset East",
            state = "South Africa",
            zip = "5850",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val continuationRows = lines.mapNotNull { line ->
            NUMERIC_CONTINUATION_PATTERN.matchEntire(line)
        }
        var continuationIndex = 0

        return buildList {
            lines.forEachIndexed { rowIndex, line ->
                val fullRow = FULL_ITEM_ROW_PATTERN.matchEntire(line)
                val descriptionRow = ITEM_DESCRIPTION_PATTERN.matchEntire(line) ?: return@forEachIndexed
                val customerCode = descriptionRow.groupValues[1].uppercase()

                val values = if (fullRow != null) {
                    ItemValues(
                        quantity = parseNumber(fullRow.groupValues[3]) ?: return@forEachIndexed,
                        unit = fullRow.groupValues[4],
                        unitPrice = parseNumber(fullRow.groupValues[5]) ?: return@forEachIndexed
                    )
                } else {
                    val continuation = continuationRows.getOrNull(continuationIndex++)
                        ?: return@forEachIndexed
                    ItemValues(
                        quantity = parseNumber(continuation.groupValues[1]) ?: return@forEachIndexed,
                        unit = continuation.groupValues[2],
                        unitPrice = parseNumber(continuation.groupValues[3]) ?: return@forEachIndexed
                    )
                }

                val printedSku = (rowIndex + 1..minOf(rowIndex + 2, lines.lastIndex))
                    .firstNotNullOfOrNull { candidateIndex ->
                        PRINTED_SKU_PATTERN.find(lines[candidateIndex])?.groupValues?.get(1)?.uppercase()
                    }
                val sku = normalizeSomersetSku(printedSku ?: CUSTOMER_CODE_TO_SKU[customerCode])
                    ?: return@forEachIndexed

                val vialCaseDivisor = if (values.unit.equals("vial", ignoreCase = true)) {
                    sku.split("-").firstNotNullOfOrNull { segment ->
                        Regex("""^(\d+)V$""").matchEntire(segment)
                            ?.groupValues
                            ?.get(1)
                            ?.toDoubleOrNull()
                    }?.takeIf { it > 1.0 }
                } else {
                    null
                }

                add(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                        quantity = vialCaseDivisor?.let { values.quantity / it } ?: values.quantity,
                        unitPrice = vialCaseDivisor?.let { roundToFourDecimals(values.unitPrice * it) }
                            ?: values.unitPrice
                    )
                )
            }
        }
    }

    private fun normalizeSomersetSku(value: String?): String? = when (value?.uppercase()) {
        null -> null
        "CH-10-1V-50" -> "CHL-10-1V-50"
        else -> value.uppercase()
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .replace("$", "")
        .toDoubleOrNull()

    private fun roundToFourDecimals(value: Double): Double = round(value * 10_000.0) / 10_000.0

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ItemValues(
        val quantity: Double,
        val unit: String,
        val unitPrice: Double
    )

    private companion object {
        const val CUSTOMER_ID = "SOMERSET EDUCATIONAL"

        val CUSTOMER_CODE_TO_SKU = mapOf(
            "30E040" to "180-500V-100",
            "30E060" to "190-500V-100",
            "30E110" to "CHL-10-1V-50",
            "30E280" to "NIT-NAT-1V-50",
            "30E310" to "PH0114-3-1B-50",
            "70E347" to "310-1-25"
        )

        val ORDER_NUMBER_PATTERN = Regex("""\bPO\d{6}\b""", RegexOption.IGNORE_CASE)
        val ITEM_DESCRIPTION_PATTERN = Regex("""^(\d{2}E\d{3})\s+(.+)$""", RegexOption.IGNORE_CASE)
        val FULL_ITEM_ROW_PATTERN = Regex(
            """^(\d{2}E\d{3})\s+(.+?)\s+([\d,]+\.\d{4})\s+(P50|vial|each)\s+([\d,]+\.\d{4})\s+\$[\d,]+\.\d{2}\s+\$[\d,]+\.\d{2}$""",
            RegexOption.IGNORE_CASE
        )
        val NUMERIC_CONTINUATION_PATTERN = Regex(
            """^([\d,]+\.\d{4})\s+(P50|vial|each)\s+([\d,]+\.\d{4})\s+\$[\d,]+\.\d{2}\s+\$[\d,]+\.\d{2}$""",
            RegexOption.IGNORE_CASE
        )
        val PRINTED_SKU_PATTERN = Regex(
            """(?:Your\s*Item\s*)?Code:\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
    }
}
