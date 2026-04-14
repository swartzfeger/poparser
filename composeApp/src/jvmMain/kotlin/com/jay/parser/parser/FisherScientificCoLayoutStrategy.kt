package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class FisherScientificCoLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Fisher Scientific Co"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString(" ")
            .replace("|", " ")
        return text.contains("FISHER SCIENTIFIC") &&
                text.contains("PRECISION LABORATORIES")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("FISHER SCIENTIFIC")) score += 100
        if (text.contains("PRECISION LABORATORIES")) score += 80
        if (text.contains("ORDER NOTES AND INSTRUCTIONS")) score += 60
        if (text.contains("CATALOG")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        return ParsedPdfFields(
            customerName = "FISHER SCIENTIFIC CO",
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = null,
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            state = null,
            zip = null,
            terms = null,
            items = findItems(textLines)
        )
    }

    private fun findOrderNumber(lines: List<String>): String? {

        // Step 1: find where the item section starts
        val itemStartIndex = lines.indexOfFirst {
            it.uppercase().contains("SUPPLIER FISHER PRODUCT")
        }

        if (itemStartIndex < 0) return null

        // Step 2: scan UPWARD from item section to find nearest PR
        for (i in itemStartIndex downTo 0) {
            val line = lines[i]
                .replace("|", " ")
                .uppercase()

            val match = Regex("""PR\d{6,}""")
                .find(line)
                ?.value

            if (match != null) {
                return match
            }

            // compact fallback
            val compact = line.replace(Regex("""[^A-Z0-9]"""), "")
            val compactMatch = Regex("""PR\d{6,}""")
                .find(compact)
                ?.value

            if (compactMatch != null) {
                return compactMatch
            }
        }

        return null
    }

    private fun findItems(lines: List<String>) =
        buildList {
            val seen = mutableSetOf<String>()

            for (raw in lines) {
                val line = cleanLine(raw)

                if (line.length < 20) continue
                if (!line.any(Char::isDigit)) continue

                val tokens = line.split(" ")

                val uomIndex = tokens.indexOfFirst {
                    val t = it.uppercase()
                    t in setOf("PK", "EA", "CS", "BX", "PE") ||
                            t.endsWith(":") && t.dropLast(1) in setOf("PK", "EA", "CS", "BX", "PE")
                }

                if (uomIndex < 2 || uomIndex + 1 >= tokens.size) continue

                val rawQty = tokens
                    .subList(0, uomIndex)
                    .lastOrNull { it.any(Char::isDigit) }
                    ?: "1"

                val rawUnit = tokens.getOrNull(uomIndex + 1)

                val rawExt = buildString {
                    append(tokens.getOrNull(uomIndex + 2) ?: "")
                    append(tokens.getOrNull(uomIndex + 3) ?: "")
                }.ifBlank { null }

                val quantity = rawQty
                    .takeIf { it.matches(Regex("""\d+""")) }
                    ?.toDoubleOrNull()
                    ?: 1.0

                val unitPrice = rawUnit
                    ?.replace(",", ".")
                    ?.toDoubleOrNull()

                val extPrice = rawExt
                    ?.replace(" ", "")
                    ?.replace(",", ".")
                    ?.let {
                        if (!it.contains(".") && it.length > 2) {
                            it.dropLast(2) + "." + it.takeLast(2)
                        } else {
                            it
                        }
                    }
                    ?.toDoubleOrNull()

                if (unitPrice == null) continue

                val fisherCatalog = tokens
                    .take(4)
                    .map { it.replace(Regex("""[^A-Z0-9-]"""), "").uppercase() }
                    .firstOrNull { it.matches(Regex("""\d{4,}""")) }
                    ?: continue

                val catalogIndex = tokens.indexOfFirst {
                    it.replace(Regex("""[^A-Z0-9-]"""), "").uppercase() == fisherCatalog
                }

                if (catalogIndex < 0 || catalogIndex + 1 >= uomIndex) continue

                val description = tokens.subList(catalogIndex + 1, uomIndex - 1)
                    .joinToString(" ")
                    .uppercase()
                    .replace(Regex("""[^A-Z0-9\s\-/.]"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val mapperDescription = ItemMapper.getItemDescription(fisherCatalog).ifBlank { null }

                if (seen.add(fisherCatalog)) {
                    add(
                        item(
                            sku = fisherCatalog,
                            description = mapperDescription ?: description,
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )
                }
            }
        }

    private fun cleanLine(line: String): String {
        return line
            .replace("|", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}