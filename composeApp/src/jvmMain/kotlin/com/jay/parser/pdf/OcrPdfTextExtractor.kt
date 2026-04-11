package com.jay.parser.pdf

import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

class OcrPdfTextExtractor {

    private val tesseract = Tesseract().apply {
        setLanguage("eng")
        setDatapath("/opt/homebrew/share/tessdata")
    }

    fun extractLines(pdfFile: File): List<PdfLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val renderer = PDFRenderer(document)
            val allLines = mutableListOf<PdfLine>()

            for (pageIndex in 0 until document.numberOfPages) {
                val image = renderer.renderImageWithDPI(pageIndex, 300f, ImageType.RGB)

                val rawText = try {
                    tesseract.doOCR(image)
                } catch (e: TesseractException) {
                    throw IllegalStateException(
                        "OCR failed on page ${pageIndex + 1} of ${pdfFile.name}: ${e.message}",
                        e
                    )
                }

                val pageLines = rawText
                    .lines()
                    .map { it.replace(Regex("""\s+"""), " ").trim() }
                    .filter { it.isNotBlank() && it != "<>" }
                    .map { line ->
                        PdfLine(
                            tokens = emptyList(),
                            text = line
                        )
                    }

                allLines.addAll(pageLines)
            }

            return allLines
        }
    }
}