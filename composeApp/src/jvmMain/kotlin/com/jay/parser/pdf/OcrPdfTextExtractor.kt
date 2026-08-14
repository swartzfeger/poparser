package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Extractor that handles OCR processing and image normalization.
 * Improved with Binarization to help Tesseract see faint fax text.
 */
class OcrPdfTextExtractor(
    private val tesseractCommand: String = defaultTesseractCommand()
) {

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
                    /*
                     * Normal documents still render at 400 DPI. Phone-scanned PDFs can
                     * declare poster-sized page dimensions, though, which would create
                     * a 100+ megapixel bitmap and exhaust the JVM heap. Cap only those
                     * oversized pages while retaining enough pixels for OCR.
                     */
                    val page = document.getPage(i)
                    val longestPageEdge = max(page.cropBox.width, page.cropBox.height)
                    val targetScale = TARGET_DPI / PDF_POINTS_PER_INCH
                    val isOversizedScan = longestPageEdge > MAX_NORMAL_PAGE_EDGE_POINTS
                    val renderScale = if (isOversizedScan) {
                        minOf(targetScale, MAX_RENDER_EDGE_PIXELS / longestPageEdge)
                    } else {
                        targetScale
                    }
                    val sourceImage = renderer.renderImage(i, renderScale, ImageType.GRAY)

                    // 2. Normalize rotation
                    val grayscaleImage = if (sourceImage.width > sourceImage.height) {
                        rotateImageClockwise90(sourceImage)
                    } else {
                        sourceImage
                    }
                    var processedImage = grayscaleImage

                    // 3. Apply binarization to normal/fax PDFs. Phone scans retain
                    // grayscale because their faint typewriter text loses detail when
                    // thresholded after the required downscaling.
                    if (!isOversizedScan) {
                        processedImage = binarizeImage(processedImage)
                    }

                    val tempImageFile = File(tempDir, "page_$i.png")
                    ImageIO.write(processedImage, "png", tempImageFile)

                    var pageText = runTesseract(tempImageFile)

                    /*
                     * Jonkman's Ricoh scans contain a ruled item table. The normal
                     * binarized PSM 3 pass reliably identifies the customer, but it
                     * drops the Ordered, Unit, and Price columns. A grayscale PSM 6
                     * pass preserves complete table rows. Keep this second pass
                     * behind the exact Jonkman fingerprint so every other OCR layout
                     * retains its existing image processing and segmentation mode.
                     */
                    if (looksLikeJonkman(pageText)) {
                        val grayscaleFile = File(tempDir, "page_${i}_jonkman_gray.png")
                        ImageIO.write(grayscaleImage, "png", grayscaleFile)
                        val tableText = runTesseract(grayscaleFile, pageSegmentationMode = 6)
                        if (tableText.isNotBlank()) pageText = tableText
                        grayscaleFile.delete()
                    }

                    /*
                     * QVORTEX purchase orders are print-to-PDF images with no text
                     * layer. The normal PSM 3 pass can omit the order number and
                     * separate wrapped SKUs from their item rows. Grayscale PSM 6
                     * retains the header and reconstructs the ruled item table.
                     */
                    if (looksLikeQvortex(pageText)) {
                        val grayscaleFile = File(tempDir, "page_${i}_qvortex_gray.png")
                        ImageIO.write(grayscaleImage, "png", grayscaleFile)
                        val tableText = runTesseract(grayscaleFile, pageSegmentationMode = 6)
                        if (tableText.isNotBlank()) pageText = tableText
                        grayscaleFile.delete()
                    }
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

    /**
     * Converts a grayscale image to high-contrast black and white (binarized).
     */
    private fun binarizeImage(source: BufferedImage): BufferedImage {
        val width = source.width
        val height = source.height
        val binarized = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)

        val g2d = binarized.createGraphics()
        g2d.drawImage(source, 0, 0, null)
        g2d.dispose()

        return binarized
    }

    private fun runTesseract(inputFile: File, pageSegmentationMode: Int = 3): String {
        val process = ProcessBuilder(
            tesseractCommand,
            inputFile.absolutePath,
            "stdout",
            "--psm",
            pageSegmentationMode.toString(),
            "quiet"
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output
    }

    private fun looksLikeJonkman(text: String): Boolean {
        val compactText = text.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
        return compactText.contains("JONKMAN") &&
                compactText.contains("EQUIPMENT") &&
                compactText.contains("2PREC")
    }

    private fun looksLikeQvortex(text: String): Boolean {
        val compactText = text.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
        return compactText.contains("QVORTEX") &&
                compactText.contains("751NW1STLN") &&
                compactText.contains("LAMARMO64759") &&
                compactText.contains("OFFICEQVORTEXCHEMICALSCOM")
    }

    private fun rotateImageClockwise90(image: BufferedImage): BufferedImage {
        val rads = Math.toRadians(90.0)
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
        private const val TARGET_DPI = 400f
        private const val PDF_POINTS_PER_INCH = 72f
        private const val MAX_NORMAL_PAGE_EDGE_POINTS = 1200f
        private const val MAX_RENDER_EDGE_PIXELS = 2400f

        private fun defaultTesseractCommand(): String {
            val os = System.getProperty("os.name").lowercase()
            val userDir = System.getProperty("user.dir")

            if (os.contains("win")) {
                val candidates = listOf(
                    File(userDir, "tesseract.exe"),                 // running from app folder
                    File(userDir, "bin/tesseract.exe"),            // bundled bin folder
                    File(userDir, "app/tesseract.exe"),            // possible packaged location
                    File("tesseract.exe"),                         // local working dir
                    File("C:/Program Files/Tesseract-OCR/tesseract.exe"),
                    File("C:/Program Files (x86)/Tesseract-OCR/tesseract.exe")
                )

                return candidates.firstOrNull { it.exists() }?.absolutePath ?: "tesseract.exe"
            }

            val macCandidates = listOf(
                File("/opt/homebrew/bin/tesseract"),   // Apple Silicon Homebrew
                File("/usr/local/bin/tesseract"),      // Intel Homebrew
                File("/opt/local/bin/tesseract"),      // MacPorts
                File("/usr/bin/tesseract")
            )

            return macCandidates.firstOrNull { it.exists() }?.absolutePath ?: "tesseract"
        }
    }
}
