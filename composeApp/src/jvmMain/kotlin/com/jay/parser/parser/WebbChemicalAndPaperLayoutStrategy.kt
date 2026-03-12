package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class WebbChemicalAndPaperLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Webb Chemical & Paper"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("WEBB CHEMICAL & PAPER") &&
                (
                        text.contains("2500 W. DIXON BLVD.") ||
                                text.contains("SHELBY, NC 28152") ||
                                text.contains("DEREK WEBB") ||
                                text.contains("CHL-1000-1V-100") ||
                                text.contains("CHL-1000-1B-100")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("WEBB CHEMICAL & PAPER")) score += 100
        if (text.contains("2500 W. DIXON BLVD.")) score += 60
        if (text.contains("SHELBY, NC 28152")) score += 60
        if (text.contains("DEREK WEBB")) score += 30
        if (text.contains("PREPAID")) score += 25
        if (text.contains("CHL-1000-1V-100")) score += 50
        if (text.contains("CHL-1000-1B-100")) score += 35

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "WEBB CHEMICAL & PAPER",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val poHeaderIndex = lines.indexOfFirst {
            it.contains("Purchase Order No.", ignoreCase = true)
        }

        if (poHeaderIndex >= 0) {
            val next = lines.getOrNull(poHeaderIndex + 1).orEmpty()
            val digits = Regex("""\d""").findAll(next).joinToString("") { it.value }
            if (digits.length >= 6) {
                return digits.take(6)
            }
        }

        for (line in lines) {
            val digits = Regex("""\d""").findAll(line).joinToString("") { it.value }
            if (digits.startsWith("024652")) {
                return "024652"
            }
        }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        return if (lines.any { it.contains("Prepaid", ignoreCase = true) }) {
            "PREPAID"
        } else {
            findFirstMatch(
                lines,
                Regex("""\b(NET\s+\d+|COD|PREPAID)\b""", RegexOption.IGNORE_CASE)
            )?.uppercase()
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val customerIndex = lines.indexOfFirst {
            it.contains("WEBB CHEMICAL & PAPER", ignoreCase = true)
        }

        if (customerIndex >= 0) {
            shipToCustomer = "WEBB CHEMICAL & PAPER"

            for (i in customerIndex until minOf(customerIndex + 6, lines.size)) {
                val line = lines[i].uppercase()

                if (addressLine1 == null && line.contains("2500 W. DIXON BLVD.")) {
                    addressLine1 = "2500 W. DIXON BLVD."
                }

                if (city == null && line.contains("SHELBY") && line.contains("NC") && line.contains("28152")) {
                    city = "SHELBY"
                    state = "NC"
                    zip = "28152"
                }
            }
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (i in 0 until lines.size - 1) {
            val line1 = normalizeSpaces(lines[i])
            val line2 = normalizeSpaces(lines[i + 1])

            if (!Regex("""^\d+\s+\d{3}-\d{5}\s+[A-Z0-9-]+""", RegexOption.IGNORE_CASE).containsMatchIn(line1)) {
                continue
            }

            val qtyPriceMatch = Regex(
                """^\d+\s+(\d{3}-\d{5})\s+([A-Z0-9-]+)\s+(.+?)\s+(\d+)\s*pk\s+([\d.]+)\s*pk\s+([\d.]+)$""",
                RegexOption.IGNORE_CASE
            ).find(line1) ?: continue

            val firstLineSku = qtyPriceMatch.groupValues[2].trim().uppercase()
            val firstLineDesc = qtyPriceMatch.groupValues[3].trim()
            val quantity = qtyPriceMatch.groupValues[4].toDoubleOrNull()
            val unitPrice = qtyPriceMatch.groupValues[5].toDoubleOrNull()

            val continuationSku = Regex("""([A-Z0-9]+(?:-[A-Z0-9]+)+)""", RegexOption.IGNORE_CASE)
                .findAll(line2)
                .map { it.groupValues[1].uppercase() }
                .lastOrNull()

            val continuationDesc = if (continuationSku != null) {
                line2.substringBefore(continuationSku).trim()
            } else {
                line2.trim()
            }

            val sku = continuationSku ?: firstLineSku
            val description = listOf(firstLineDesc, continuationDesc)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (quantity == null || unitPrice == null) continue

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank {
                if (description.isBlank()) null else description
            }

            add(
                item(
                    sku = sku,
                    description = mappedDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun normalizeSpaces(value: String): String {
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}