package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class CarolinaBiologicalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Carolina Biological"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("CAROLINA BIOLOGICAL") &&
                (
                        text.contains("WHITSETT") ||
                                text.contains("CBS ORDER #") ||
                                text.contains("P.O. NUMBER") ||
                                text.contains("PURCHASE ORDER COVER SHEET")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("CAROLINA BIOLOGICAL")) score += 100
        if (text.contains("WHITSETT")) score += 60
        if (text.contains("P.O. NUMBER")) score += 40
        if (text.contains("CBS ORDER #")) score += 40
        if (text.contains("PURCHASE ORDER COVER SHEET")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findCarolinaShipTo(textLines)

        return ParsedPdfFields(
            customerName = findCustomerName(textLines),
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findCarolinaItems(textLines)
        )
    }

    private fun findCustomerName(textLines: List<String>): String? {
        val mergedLine = textLines.firstOrNull {
            it.contains("CAROLINA BIOLOGICAL SUPPLY", ignoreCase = true)
        } ?: return null

        return extractRightSideCustomerName(mergedLine)
            ?: "CAROLINA BIOLOGICAL SUPPLY"
    }

    private fun findOrderNumber(textLines: List<String>): String? {
        return findFirstMatch(
            textLines,
            Regex("""P\.?O\.?\s*NUMBER\s+(\d{4,})""", RegexOption.IGNORE_CASE)
        ) ?: findFirstMatch(
            textLines,
            Regex("""PO#:\s*_?\s*(\d{4,})""", RegexOption.IGNORE_CASE)
        ) ?: textLines.asSequence()
            .mapNotNull { Regex("""\b(9\d{5})\b""").find(it)?.groupValues?.get(1) }
            .firstOrNull()
    }

    private fun findTerms(textLines: List<String>): String? {
        val text = textLines.joinToString(" ").uppercase()
        return if (Regex("""1\s*%\s*10\s*NET\s*30""").containsMatchIn(text.replace(Regex("""\s+"""), " ")) ||
            text.replace(Regex("""[^A-Z0-9%]"""), "").contains("1%10NET30")
        ) {
            "1% 10 NET 30"
        } else {
            null
        }
    }

    private fun findCarolinaShipTo(textLines: List<String>): ShipToBlock {
        val mergedCustomerLine = textLines.firstOrNull {
            it.contains("CAROLINA BIOLOGICAL SUPPLY", ignoreCase = true)
        }

        val shipToCustomer = mergedCustomerLine
            ?.let { extractRightSideCustomerName(it) }
            ?: "CAROLINA BIOLOGICAL SUPPLY"

        val addressLine2 = textLines.firstOrNull {
            it.contains("ATTN:", ignoreCase = true)
        }?.let { cleanAttn(it) }

        val addressLine1 = textLines.firstOrNull {
            it.contains("JUDGE ADAMS", ignoreCase = true)
        }?.let { cleanJudgeAdamsLine(it) }

        val cityStateZipLine = textLines.firstOrNull {
            it.contains("WHITSETT", ignoreCase = true) && it.contains("27377")
        }?.replace("WHITSETTNC", "WHITSETT NC", ignoreCase = true)

        val parsed = parseCityStateZip(cityStateZipLine)

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = parsed.city,
            state = parsed.state,
            zip = parsed.zip
        )
    }

    private fun findCarolinaItems(lines: List<String>) =
        buildList {
            val normalizedLines = lines.map { it.replace(Regex("""\s+"""), " ").trim() }

            for (index in normalizedLines.indices) {
                val line = normalizedLines[index]
                val rowMatch = itemRowRegex.find(line) ?: continue

                val itemText = rowMatch.groupValues[3].trim()
                val quantity = rowMatch.groupValues[4].toDoubleOrNull()
                val unitPrice = rowMatch.groupValues[6].toDoubleOrNull()

                val skuFromRow = skuTokenRegex.find(itemText)?.value
                val previousSku = normalizedLines.getOrNull(index - 1)?.takeIf { looksLikeSkuFragment(it) }
                val nextSkuSuffix = normalizedLines.getOrNull(index + 1)?.takeIf { looksLikeSkuSuffix(it) }

                val rawSku = when {
                    skuFromRow != null -> skuFromRow
                    previousSku != null -> previousSku
                    else -> null
                } ?: continue

                val sku = normalizeCarolinaSku(rawSku, nextSkuSuffix)
                val description = cleanDescription(itemText, skuFromRow)

                add(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku).ifBlank {
                            description.ifBlank { null }
                        },
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

    private val itemRowRegex = Regex(
        """^(\d+)\s+(\d+)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+(EA|BX|PK|GR)\s+(\d+(?:\.\d{1,4})?)\s+([\d,]+(?:\.\d{2})?)\s+\d{2}/\d{2}/\d{2}$""",
        RegexOption.IGNORE_CASE
    )

    private val skuTokenRegex = Regex(
        """\b[A-Z0-9]+(?:-[A-Z0-9]+)+-?\b""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanDescription(itemText: String, skuFromRow: String?): String {
        return if (skuFromRow != null) {
            itemText.removePrefix(skuFromRow).trim()
        } else {
            itemText.trim()
        }
    }

    private fun looksLikeSkuFragment(line: String): Boolean {
        val cleaned = line.trim()
        if (cleaned.length < 5) return false
        if (cleaned.contains(" ")) return false
        if (!cleaned.contains("-")) return false

        // Carolina sometimes emits a supplier SKU fragment on its own line with
        // a trailing dash, then the final suffix on the line after the item row:
        //
        // 180-190-1V-
        // 2 895511 ACID/BASE INDICATOR STRIPS 200 EA 1.7400 348.00 06/16/26
        // 100
        //
        // The generic skuTokenRegex does not reliably match a trailing dash, so
        // allow that fragment shape here.
        return Regex("""^[A-Z0-9]+(?:-[A-Z0-9]+)*-?$""", RegexOption.IGNORE_CASE).matches(cleaned)
    }

    private fun looksLikeSkuSuffix(line: String): Boolean {
        val cleaned = line.trim()
        return cleaned == "0" || cleaned == "50" || cleaned == "100"
    }

    private fun normalizeCarolinaSku(rawSku: String, nextSuffix: String?): String {
        var cleaned = rawSku
            .uppercase()
            .replace(Regex("""[^A-Z0-9-]"""), "-")
            .replace(Regex("""-+"""), "-")
            .trim('-')

        if (rawSku.trim().endsWith("-") && nextSuffix != null) {
            cleaned = "$cleaned-$nextSuffix"
        } else if (nextSuffix == "0" && cleaned.endsWith("-10")) {
            cleaned += "0"
        }

        return normalizeSku(cleaned)
    }

    private fun extractRightSideCustomerName(line: String): String? {
        val upper = line.uppercase()
        val marker = "PRECISION LABORATORIES"
        val idx = upper.indexOf(marker)

        val remainder = if (idx >= 0) {
            line.substring(idx + marker.length).trim()
        } else {
            line.trim()
        }

        return remainder
            .replace(Regex("""\s*-\s*BP(\d+)""", RegexOption.IGNORE_CASE), " - BP$1")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { null }
    }

    private fun cleanAttn(line: String): String {
        val attnPart = line.substring(line.indexOf("ATTN:", ignoreCase = true))
        return attnPart
            .replace("KITTINGDEPT", "KITTING DEPT", ignoreCase = true)
            .replace("FULFILLMENTDEPT", "FULFILLMENT DEPT", ignoreCase = true)
            .replace("CURRICULUMDEPT", "CURRICULUM DEPT", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanJudgeAdamsLine(line: String): String {
        val match = Regex("""6537\s+JUDGE\s+ADAMS\s+RD""", RegexOption.IGNORE_CASE).find(line)
        return match?.value?.uppercase() ?: line.trim()
    }

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val normalized = line
            .trim()
            .replace("WHITSETTNC", "WHITSETT NC", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")

        val match = Regex("""(WHITSETT)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""", RegexOption.IGNORE_CASE).find(normalized)
        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].uppercase(),
                state = match.groupValues[2].uppercase(),
                zip = match.groupValues[3]
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}
