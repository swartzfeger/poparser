package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class ElectronicControlsDesignLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Electronic Controls Design"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("ELECTRONICCONTROLSDESIGNINC") &&
                (
                        text.contains("74247") ||
                                text.contains("4287BSEINTERNATIONALWAY") ||
                                text.contains("MILWAUKIEOR97222") ||
                                text.contains("2801008513")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("ELECTRONICCONTROLSDESIGNINC")) score += 100
        if (text.contains("74247")) score += 80
        if (text.contains("4287BSEINTERNATIONALWAY")) score += 60
        if (text.contains("MILWAUKIEOR97222")) score += 60
        if (text.contains("2801008513")) score += 80
        if (text.contains("FLUXOMETER")) score += 20
        if (text.contains("BLUELITMUS")) score += 20
        if (text.contains("NEUTRALPH")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val customerName = parseCustomerName(clean)
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = customerName,
            addressLine1 = parseAddressLine1(clean),
            addressLine2 = null,
            city = parseCity(clean),
            state = parseState(clean),
            zip = parseZip(clean),
            terms = mappedCustomer?.terms,
            items = parseItems(clean, mappedCustomer?.priceLevel)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ELECTRONICCONTROLSDESIGNINC")
        }?.let { "Electronic Controls Design Inc." }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(line)

            val inline = Regex(
                """Purchase\s*Order\s*[:#]?\s*(\d{4,10})""",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (inline != null) {
                return inline.groupValues[1].trim()
            }

            if (compactLine == "PURCHASEORDER") {
                for (j in (i + 1)..minOf(i + 4, lines.lastIndex)) {
                    val candidate = lines[j].replace(Regex("""\s+"""), " ").trim()

                    // Example: "Milwaukie,OR97222U.S.A. 74247"
                    val trailing = Regex("""(\d{4,10})$""").find(candidate)
                    if (trailing != null) {
                        return trailing.groupValues[1]
                    }

                    if (candidate.matches(Regex("""\d{4,10}"""))) {
                        return candidate
                    }
                }
            }
        }

        return null
    }

    private fun parseAddressLine1(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("4287BSEINTERNATIONALWAY")
        }?.let { "4287-B S.E. International Way" }
    }

    private fun parseCity(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("MILWAUKIEOR97222")
        }?.let { "Milwaukie" }
    }

    private fun parseState(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("MILWAUKIEOR97222")
        }?.let { "OR" }
    }

    private fun parseZip(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("MILWAUKIEOR97222")
        }?.let { "97222" }
    }

    private fun parseItems(lines: List<String>, priceLevel: String?) = buildList {
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val first = lines[i].replace(Regex("""\s+"""), " ").trim()

            if (!first.matches(Regex("""^\d+\s+G\d+-\d+-\d+\s+.+$""", RegexOption.IGNORE_CASE))) {
                continue
            }

            var rawSku: String? = null
            var quantity: Double? = null
            val descriptionParts = mutableListOf<String>()

            // Line 20:
            // 10000 G04-3589-75 15"FLUXOMETER BLUELITMUS 280- A BOX 5 95.00 March18,2026 475.00
            val firstMatch = Regex(
                """^\d+\s+G\d+-\d+-\d+\s+(.+?)\s+(280-)\s+[A-Z]\s+[A-Z]{2,5}\s+(\d+(?:,\d{3})?)\s+[\d,]+\.\d{2}\s+[A-Z][a-z]+\d{1,2},\d{4}\s+[\d,]+\.\d{2}$""",
                RegexOption.IGNORE_CASE
            ).find(first)

            if (firstMatch == null) continue

            descriptionParts += firstMatch.groupValues[1].trim()
            val skuHead = firstMatch.groupValues[2].trim()
            quantity = firstMatch.groupValues[3].replace(",", "").toDoubleOrNull()

            if (quantity == null) continue

            // Line 21:
            // NEUTRALPH 100-8513
            val second = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val secondMatch = Regex(
                """^(.+?)\s+(100-\d+)$""",
                RegexOption.IGNORE_CASE
            ).find(second)

            if (secondMatch != null) {
                val desc2 = secondMatch.groupValues[1].trim()
                if (desc2.isNotBlank()) descriptionParts += desc2
                rawSku = skuHead + secondMatch.groupValues[2].trim()
            }

            // Line 22:
            // PAPER
            val third = lines.getOrNull(i + 2)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            if (third.isNotBlank() &&
                !third.startsWith("Subtotal:", ignoreCase = true) &&
                !third.startsWith("Invoice", ignoreCase = true) &&
                !third.startsWith("Total", ignoreCase = true)
            ) {
                descriptionParts += third
            }

            val sku = rawSku?.let { normalizeSku(it) } ?: continue

            val poDescription = descriptionParts.joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val finalDescription = ItemMapper.getItemDescription(sku).ifBlank { poDescription }

            val mappedPrice = ItemMapper.getItemPrice(sku, priceLevel)
            val unitPrice = if (mappedPrice == 0.0) null else mappedPrice

            val key = "$sku|$quantity"
            if (!seen.add(key)) continue

            add(
                item(
                    sku = sku,
                    description = finalDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }
}