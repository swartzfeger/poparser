package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Extractor that handles OCR processing and image normalization (rotation).
 * Returns a flat list of lines. Segmentation is handled upstream by OrderFileParser.
 */
class OcrPdfTextExtractor(
    private val tesseractCommand: String = defaultTesseractCommand()
) {

    /**
     * Extracts text and returns a flat list of lines.
     */
    fun extractLines(file: File): List<PdfLine> {
        return performOcr(file)
    }

    private fun performOcr(file: File): List<PdfLine> {
        val allLines = mutableListOf<PdfLine>()
        val tempDir = Files.createTempDirectory("po_parser_ocr").toFile()

        try {
            Loader.loadPDF(file).use { document ->
                val renderer = PDFRenderer(document)
                for (i in 0 until document.numberOfPages) {
                    // 300 DPI is optimal for noisy fax documents
                    val sourceImage = renderer.renderImageWithDPI(i, 300f, ImageType.RGB)

                    // Normalize: Rotate 90deg if it's a landscape scan (common in Fisher PO faxes)
                    val processedImage = if (sourceImage.width > sourceImage.height) {
                        rotateImage(sourceImage, 90.0)
                    } else {
                        sourceImage
                    }

                    val tempImageFile = File(tempDir, "page_$i.png")
                    ImageIO.write(processedImage, "png", tempImageFile)

                    val pageText = runTesseract(tempImageFile)
                    pageText.lines()
                        .filter { it.isNotBlank() }
                        .forEach { allLines.add(PdfLine(tokens = emptyList(), text = it.trim())) }

                    tempImageFile.delete()
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
        return allLines
    }

    private fun runTesseract(inputFile: File): String {
        // PSM 3: Fully automatic page segmentation (best for varied structured documents)
        val process = ProcessBuilder(tesseractCommand, inputFile.absolutePath, "stdout", "--psm", "3", "quiet")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output
    }

    private fun rotateImage(image: BufferedImage, angle: Double): BufferedImage {
        val rads = Math.toRadians(angle)
        val sin = Math.abs(Math.sin(rads))
        val cos = Math.abs(Math.cos(rads))
        val w = image.width
        val h = image.height
        val newWidth = Math.floor(w * cos + h * sin).toInt()
        val newHeight = Math.floor(h * cos + w * sin).toInt()

        val rotated = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = rotated.createGraphics()
        val at = AffineTransform()
        at.translate((newWidth - w) / 2.0, (newHeight - h) / 2.0)
        at.rotate(rads, w / 2.0, h / 2.0)
        g2d.transform = at
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        return rotated
    }

    companion object {
        private fun defaultTesseractCommand(): String {
            val os = System.getProperty("os.name").lowercase()
            return if (os.contains("win")) "tesseract.exe" else "tesseract"
        }
    }
}