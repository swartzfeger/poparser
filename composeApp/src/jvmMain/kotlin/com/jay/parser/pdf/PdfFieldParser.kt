package com.jay.parser.pdf

import com.jay.parser.parser.StrategyRegistry
import kotlin.math.abs

class PdfFieldParser(
    private val registry: StrategyRegistry = StrategyRegistry.default()
) {

    fun parse(lines: List<PdfLine>): ParsedPdfFields {
        val textLines = lines.map { it.text.trim() }.filter { it.isNotBlank() }

        println("==================================================")
        println("PDF FIELD PARSER")
        println("==================================================")
        println("Input lines: ${textLines.size}")

        if (textLines.isNotEmpty()) {
            println("---- TEXT LINES ----")
            textLines.forEachIndexed { index, line ->
                println("${index + 1}: $line")
            }
        } else {
            println("No text lines supplied to PdfFieldParser.")
        }

        println("---- STRATEGY SELECTION ----")
        val strategy = registry.choose(textLines)

        return if (strategy != null) {
            println("PdfFieldParser using strategy: ${strategy.name}")

            val parsed = strategy.parse(textLines)

            if (strategy.name == "FRESENIUS MEDICAL") {
                parseFreseniusWithFallback(lines, parsed)
            } else {
                parsed
            }
        } else {
            println("PdfFieldParser using strategy: Generic")
            parseGeneric(textLines)
        }
    }

    private fun parseFreseniusWithFallback(
        lines: List<PdfLine>,
        base: ParsedPdfFields
    ): ParsedPdfFields {
        if (base.items.isNotEmpty()) return base

        val cleanedLines = lines
            .map { dedupeAndRebuildLine(it) }
            .filter { it.isNotBlank() }

        val items = buildList {
            val seen = mutableSetOf<String>()

            for (i in cleanedLines.indices) {
                val current = normalize(cleanedLines[i])

                val materialMatch = Regex(
                    """YOUR\s+MATERIAL\s+NUMBER:\s*([A-Z0-9./-]+)""",
                    RegexOption.IGNORE_CASE
                ).find(current) ?: continue

                val sku = materialMatch.groupValues[1].trim()

                var quantity: Double? = null
                var unitPrice: Double? = null
                var description: String? = null

                for (j in (i - 1) downTo maxOf(0, i - 8)) {
                    val candidate = normalize(cleanedLines[j])

                    if (
                        description == null &&
                        candidate.isNotBlank() &&
                        !candidate.contains("PURCHASE ORDER", true) &&
                        !candidate.contains("FRESENIUS", true) &&
                        !candidate.contains("SHIP TO", true) &&
                        !candidate.contains("BILL TO", true) &&
                        !candidate.contains("DELIVERY DATE", true) &&
                        !candidate.contains("SHIPPING INSTRUCTIONS", true) &&
                        !candidate.contains("TOTAL PRICE EXCLUDING TAX", true) &&
                        !candidate.contains("YOUR MATERIAL NUMBER", true)
                    ) {
                        val looksNumeric = Regex(
                            """(?:\d+\s+)?[A-Z0-9./-]+\s+\d+(?:\.\d+)?\s+[A-Z]+\s+\d+(?:\.\d+)?\s+\d+(?:\.\d+)?$""",
                            RegexOption.IGNORE_CASE
                        ).matches(candidate)

                        if (!looksNumeric) {
                            description = candidate
                        }
                    }

                    val numericMatch = Regex(
                        """(?:\d+\s+)?[A-Z0-9./-]+\s+(\d+(?:\.\d+)?)\s+([A-Z]+)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""",
                        RegexOption.IGNORE_CASE
                    ).find(candidate)

                    if (numericMatch != null) {
                        quantity = numericMatch.groupValues[1].toDoubleOrNull()
                        unitPrice = numericMatch.groupValues[3].toDoubleOrNull()
                    }

                    if (quantity != null && unitPrice != null && description != null) {
                        break
                    }
                }

                if (quantity == null || unitPrice == null) continue

                val key = "$sku|$quantity|$unitPrice"
                if (!seen.add(key)) continue

                add(
                    ParsedPdfItem(
                        sku = sku,
                        description = description ?: sku,
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )
            }
        }

        return base.copy(items = items)
    }

    private fun dedupeAndRebuildLine(line: PdfLine): String {
        if (line.tokens.isEmpty()) return line.text.trim()

        val deduped = mutableListOf<PdfToken>()

        for (token in line.tokens.sortedBy { it.x }) {
            val text = token.text.trim()
            if (text.isEmpty()) continue

            val isDuplicate = deduped.any { existing ->
                existing.text == token.text &&
                        abs(existing.x - token.x) < 0.2f &&
                        abs(existing.y - token.y) < 0.2f &&
                        abs(existing.width - token.width) < 0.2f
            }

            if (!isDuplicate) {
                deduped.add(token)
            }
        }

        if (deduped.isEmpty()) return ""

        val sb = StringBuilder()
        var previous: PdfToken? = null

        for (token in deduped.sortedBy { it.x }) {
            val cleaned = token.text.trim()
            if (cleaned.isEmpty()) continue

            if (previous == null) {
                sb.append(cleaned)
            } else {
                val previousRightEdge = previous.x + previous.width
                val gap = token.x - previousRightEdge

                if (shouldInsertSpace(previous.text, cleaned, gap)) {
                    sb.append(' ')
                }

                sb.append(cleaned)
            }

            previous = token
        }

        return sb.toString()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun shouldInsertSpace(previousText: String, currentText: String, gap: Float): Boolean {
        if (gap > 2.5f) return true

        val prev = previousText.lastOrNull() ?: return false
        val curr = currentText.firstOrNull() ?: return false

        if (curr in listOf(',', '.', ':', ';', ')', '%')) return false
        if (prev in listOf('(', '/', '-')) return false

        return false
    }

    private fun parseGeneric(lines: List<String>): ParsedPdfFields {
        return ParsedPdfFields(
            customerName = null,
            orderNumber = findGenericOrderNumber(lines),
            items = emptyList()
        )
    }

    private fun findGenericOrderNumber(lines: List<String>): String? {
        val patterns = listOf(
            Regex("""(?:PO NUMBER|P\.O\. NUMBER)\s*:?\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:PURCHASE ORDER)\s*#?\s*:?\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        )

        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.length >= 4) return candidate
                }
            }
        }

        return null
    }

    private fun normalize(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}