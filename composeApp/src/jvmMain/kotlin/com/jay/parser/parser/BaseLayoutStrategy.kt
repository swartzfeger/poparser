package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

abstract class BaseLayoutStrategy : LayoutStrategy {

    protected val blockExtractor = CandidateBlockExtractor()
    protected val shipToScorer = ShipToBlockScorer()
    protected val shipToInterpreter = ShipToInterpreter()

    protected fun nonBlankLines(lines: List<String>): List<String> {
        return lines.map { it.trim() }.filter { it.isNotBlank() }
    }

    protected fun findFirstMatch(lines: List<String>, regex: Regex): String? {
        for (line in lines) {
            val match = regex.find(line)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    protected fun bestShipTo(lines: List<String>): InterpretedShipTo? {
        val blocks = blockExtractor.extract(lines)
        val scored = shipToScorer.score(blocks)
        return scored.firstOrNull()?.let { shipToInterpreter.interpret(it.block) }
    }

    protected fun normalizeSku(input: String): String {
        var sku = input.trim()

        sku = sku.replace(Regex("""\s+INDIGO$""", RegexOption.IGNORE_CASE), "")
        sku = sku.replace(Regex("""^IPA\s+""", RegexOption.IGNORE_CASE), "")
        sku = sku.replace(Regex("""-CANADA$""", RegexOption.IGNORE_CASE), "")
        sku = sku.replace(Regex("""/EDMX$""", RegexOption.IGNORE_CASE), "")
        // Specific fix for Maintex partial extractions
        if (sku == "PIN-MNTX-") sku = "PIN-MNTX-100"

        return sku.trim()
    }

    protected fun emptyResult(): ParsedPdfFields = ParsedPdfFields(
        items = emptyList()
    )

    protected fun item(
        sku: String? = null,
        description: String? = null,
        quantity: Double? = null,
        unitPrice: Double? = null
    ): ParsedPdfItem {
        return ParsedPdfItem(
            sku = sku,
            description = description,
            quantity = quantity,
            unitPrice = unitPrice
        )
    }
}