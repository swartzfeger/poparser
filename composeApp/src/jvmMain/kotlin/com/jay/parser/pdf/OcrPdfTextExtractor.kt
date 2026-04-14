package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import javax.imageio.ImageIO

class OcrPdfTextExtractor(
    private val tesseractCommand: String = defaultTesseractCommand()
) {

    fun extractLines(pdfFile: File): List<PdfLine> {
        Loader.loadPDF(pdfFile).use { document ->
            val renderer = PDFRenderer(document)
            val allLines = mutableListOf<PdfLine>()

            // Create a single temp directory for this file's OCR process
            val tempDir = Files.createTempDirectory("po-parser-ocr").toFile()

            try {
                for (pageIndex in 0 until document.numberOfPages) {
                    // Render at 300 DPI for high OCR accuracy
                    val sourceImage = renderer.renderImageWithDPI(pageIndex, 300f, ImageType.RGB)

                    // FISHER FIX: If width > height, it's a landscape scan rotated 90 deg.
                    // We normalize it to portrait here so Tesseract reads rows correctly.
                    val normalizedImage = if (sourceImage.width > sourceImage.height) {
                        rotateImage(sourceImage, 90.0)
                    } else {
                        sourceImage
                    }

                    // Save the normalized image to disk for Tesseract to consume
                    val pageImageFile = File(tempDir, "page_${pageIndex}.png")
                    ImageIO.write(normalizedImage, "png", pageImageFile)

                    // Run Tesseract and get the text
                    val outputBase = File(tempDir, "page_${pageIndex}_out")
                    val pageText = runTesseract(pageImageFile, outputBase)

                    // Clean up and convert text into the app's internal PdfLine format
                    val lines = pageText.lines()
                        .map { it.replace(Regex("\\s+"), " ").trim() }
                        .filter { it.isNotBlank() }
                        .map { PdfLine(tokens = emptyList(), text = it) }

                    allLines.addAll(lines)
                }
            } finally {
                // Ensure we clean up all images/text files from the temp directory
                tempDir.deleteRecursively()
            }

            return allLines
        }
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

    private fun runTesseract(inputFile: File, outputBase: File): String {
        val process = ProcessBuilder(tesseractCommand, inputFile.absolutePath, outputBase.absolutePath)
            .redirectErrorStream(true)
            .start()

        process.waitFor()

        val txtFile = File("${outputBase.absolutePath}.txt")
        return if (txtFile.exists()) {
            txtFile.readText(Charsets.UTF_8)
        } else {
            ""
        }
    }

    companion object {
        private fun defaultTesseractCommand(): String {
            val os = System.getProperty("os.name").lowercase()
            val appDir = File(System.getProperty("user.dir"))

            return if (os.contains("win")) {
                val bundled = File(appDir, "ocr/tesseract.exe")
                if (bundled.exists()) bundled.absolutePath else "tesseract.exe"
            } else {
                val bundled = File(appDir, "ocr/tesseract")
                if (bundled.exists()) bundled.absolutePath else "tesseract"
            }
        }
    }
}