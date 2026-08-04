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

            val firstMatch = ITEM_ROW_PATTERN.find(first) ?: continue
            val rowDetails = firstMatch.groupValues[1].trim()
            val quantity = firstMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue

            val second = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()
            val third = lines.getOrNull(i + 2)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val completeSkuMatch = COMPLETE_SKU_PATTERN.find(rowDetails)
            val splitSkuSuffixMatch = if (completeSkuMatch == null && rowDetails.endsWith("280-")) {
                SPLIT_SKU_SUFFIX_PATTERN.find(second)
            } else {
                null
            }
            val rawSku = completeSkuMatch?.value
                ?: splitSkuSuffixMatch?.let { "280-${it.value}" }
                ?: continue
            val sku = normalizeSku(rawSku)

            val descriptionParts = mutableListOf<String>()
            rowDetails
                .removeMatchedText(completeSkuMatch)
                .removeSuffix("280-")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let(descriptionParts::add)

            second
                .removeMatchedText(splitSkuSuffixMatch)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let(descriptionParts::add)

            if (third.isNotBlank() &&
                !third.startsWith("Subtotal:", ignoreCase = true) &&
                !third.startsWith("Invoice", ignoreCase = true) &&
                !third.startsWith("Total", ignoreCase = true)
            ) {
                descriptionParts += third
            }

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

    private fun String.removeMatchedText(match: MatchResult?): String =
        if (match == null) this else removeRange(match.range)

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }

    private companion object {
        val ITEM_ROW_PATTERN = Regex(
            """^\d+\s+G\d+-\d+-\d+\s+(.+?)\s+[A-Z]\s+[A-Z]{2,5}\s+([\d,]+(?:\.\d+)?)\s+[\d,]+\.\d{2,3}\s+[A-Z][a-z]+\s*\d{1,2},\s*\d{4}\s+[\d,]+\.\d{2}$""",
            RegexOption.IGNORE_CASE
        )
        val COMPLETE_SKU_PATTERN = Regex("""\b280-\d{2,3}-\d+\b""", RegexOption.IGNORE_CASE)
        val SPLIT_SKU_SUFFIX_PATTERN = Regex("""\b\d{2,3}-\d+\b""", RegexOption.IGNORE_CASE)
    }
}
