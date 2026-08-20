package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
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
            // Fresenius-only fallback:
            // The token-grouped path is corrupting the item block for this PDF,
            // while plain PDF text contains the exact three-line item block correctly.
            if (pdfFile.name.contains("FRESENIUS MEDICAL", ignoreCase = true)) {
                return extractPlainLines(document)
            }

            /*
             * George's six-page POs repeat the same coordinates on every page.
             * The positioned path therefore interleaves page one with five pages
             * of terms. All order data is on page one; later pages are boilerplate.
             */
            if (document.pages.count > 1) {
                val firstPageLines = extractPlainLines(document, endPage = 1)
                if (looksLikeGeorges(firstPageLines)) {
                    return firstPageLines
                }
                if (looksLikeJayhawk(firstPageLines)) {
                    return firstPageLines
                }
                if (looksLikePinetree(firstPageLines)) {
                    return extractPlainLines(document)
                }
            }

            val stripper = PositionTextStripper()
            stripper.sortByPosition = true
            stripper.startPage = 1
            stripper.endPage = document.pages.count

            stripper.getText(document)

            val positionedLines = groupIntoLines(stripper.tokens)

            /*
             * WESTLAB's two-page Crystal Reports PDFs reuse the same vertical
             * coordinates on both pages. Grouping all tokens by Y interleaves the
             * page-two instructions into page-one headers and the first item row.
             * Plain PDFBox extraction keeps pages sequential and is limited to this
             * exact layout fingerprint so other customer extraction is unchanged.
             */
            if (looksLikeWestlab(positionedLines)) {
                return extractPlainLines(document)
            }

            /*
             * StatLab's second page reuses page-one Y coordinates for its terms.
             * Positioned extraction interleaves the legal text into the ship-to
             * address and item table, while plain extraction keeps pages sequential.
             */
            if (looksLikeStatlab(positionedLines)) {
                return extractPlainLines(document)
            }

            /*
             * Contec's two-page production-order PDFs reuse page-one coordinates
             * for their page-two routing instructions. Plain extraction preserves
             * the delivery address and item table as sequential page-one lines.
             */
            if (looksLikeContecDistribution(positionedLines)) {
                return extractPlainLines(document)
            }

            return positionedLines
        }
    }

    private fun extractPlainLines(
        document: PDDocument,
        endPage: Int = document.pages.count
    ): List<PdfLine> {
        val plainStripper = PDFTextStripper().apply {
            sortByPosition = true
            startPage = 1
            this.endPage = endPage
        }

        return plainStripper.getText(document)
            .lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .map { line -> PdfLine(tokens = emptyList(), text = line) }
    }

    private fun looksLikeWestlab(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        return compactText.contains("WESTLABNORTHAMERICA") &&
                compactText.contains("WESTLABVENDORPACKAGINGREQUIREMENTS")
    }

    private fun looksLikeStatlab(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        return compactText.contains("2090COMMERCEDRIVE") &&
                compactText.contains("MCKINNEYTX75069") &&
                compactText.contains("QUALITYSTATLABCOM") &&
                compactText.contains("MOZORNIOSTATLABCOM") &&
                compactText.contains("PONUMBER")
    }

    private fun looksLikeContecDistribution(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        return compactText.contains("CONTECLOGISTICSAT18664364804") &&
                compactText.contains("LUNDERWOODCONTECINCCOM") &&
                compactText.contains("CONTECRECEIVINGAT18646998303") &&
                compactText.contains("LINEYOURPARTNOYOURDESCRIPTIONDOCKQTYDUE")
    }

    private fun looksLikeGeorges(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        return compactText.contains("REPORTLISTINGPOI9") &&
                compactText.contains("GEORGESPROCUREMENTCO3") &&
                compactText.contains("VENDOR33006") &&
                compactText.contains("CASSVILLEMRO") &&
                compactText.contains("VENDORITEMNUMBER")
    }

    private fun looksLikeJayhawk(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        val looksLikeTexas =
            compactText.contains("INFOJAYHAWKSALESCOM") &&
                    compactText.contains("2613INDUSTRIALLN") &&
                    compactText.contains("GARLANDTX75041")

        val looksLikeWisconsin =
            compactText.contains("JAYHAWKSALESMIDWEST") &&
                    compactText.contains("2995SMOORLANDRD") &&
                    compactText.contains("NEWBERLINWI53151")

        return looksLikeTexas || looksLikeWisconsin
    }

    private fun looksLikePinetree(lines: List<PdfLine>): Boolean {
        val compactText = lines.joinToString("") { it.text }
            .uppercase()
            .replace(Regex("""[^A-Z0-9]"""), "")

        return compactText.contains("PINETREEINSTRUMENTSINC") &&
                compactText.contains("PURCHASEORDER") &&
                compactText.contains("VENDORPRODCODE") &&
                compactText.contains("169LEXINGTONCOURT")
    }

    private fun groupIntoLines(tokens: List<PdfToken>): List<PdfLine> {
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
