package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

data class PdfToken(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float
)

data class PdfLine(
    val tokens: List<PdfToken>,
    val text: String
)

class PdfTextExtractor {

    fun extractLines(pdfFile: File): List<PdfLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val stripper = PositionTextStripper()
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = document.pages.count

            stripper.getText(document)

            return groupIntoLines(stripper.tokens)
        }
    }

    private fun groupIntoLines(tokens: List<PdfToken>): List<PdfLine> {
        // IMPORTANT:
        // Sort top-to-bottom, then left-to-right.
        val sorted = tokens.sortedWith(
            compareBy<PdfToken> { it.y }.thenBy { it.x }
        )

        val lines = mutableListOf<MutableList<PdfToken>>()
        val yTolerance = 3f

        for (token in sorted) {
            val existingLine = lines.find { line ->
                abs(line.first().y - token.y) < yTolerance
            }

            if (existingLine != null) {
                existingLine.add(token)
            } else {
                lines.add(mutableListOf(token))
            }
        }

        return lines
            .map { lineTokens ->
                val sortedLine = lineTokens.sortedBy { it.x }
                PdfLine(
                    tokens = sortedLine,
                    text = rebuildLineText(sortedLine)
                )
            }
            .sortedBy { it.tokens.firstOrNull()?.y ?: Float.MAX_VALUE }
    }

    private fun rebuildLineText(tokens: List<PdfToken>): String {
        if (tokens.isEmpty()) return ""

        val sb = StringBuilder()
        var previous: PdfToken? = null

        for (token in tokens) {
            val cleaned = token.text.trim()
            if (cleaned.isEmpty()) continue

            if (previous == null) {
                sb.append(cleaned)
            } else {
                val previousRightEdge = previous.x + previous.width
                val gap = token.x - previousRightEdge

                val needsSpace = shouldInsertSpace(previous.text, cleaned, gap)
                if (needsSpace) sb.append(' ')

                sb.append(cleaned)
            }

            previous = token
        }

        return sb.toString()
            .replace(Regex("\\s+"), " ")
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

    private class PositionTextStripper : PDFTextStripper() {
        val tokens = mutableListOf<PdfToken>()

        override fun processTextPosition(text: TextPosition) {
            val value = text.unicode
            if (!value.isNullOrBlank()) {
                tokens.add(
                    PdfToken(
                        text = value,
                        x = text.xDirAdj,
                        y = text.yDirAdj,
                        width = text.widthDirAdj
                    )
                )
            }
        }
    }
}