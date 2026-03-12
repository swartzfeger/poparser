package com.jay.parser.pdf

import com.jay.parser.parser.StrategyRegistry

class PdfFieldParser(
    private val registry: StrategyRegistry = StrategyRegistry.default()
) {

    fun parse(lines: List<PdfLine>): ParsedPdfFields {
        val textLines = lines.map { it.text.trim() }.filter { it.isNotBlank() }

        val strategy = registry.choose(textLines)

        return if (strategy != null) {
            println("PdfFieldParser using strategy: ${strategy.name}")
            strategy.parse(textLines)
        } else {
            println("PdfFieldParser using strategy: Generic")
            parseGeneric(textLines)
        }
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
}