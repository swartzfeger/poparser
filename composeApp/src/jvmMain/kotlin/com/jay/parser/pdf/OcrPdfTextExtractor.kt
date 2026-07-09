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
 * Extractor that handles OCR processing and image normalization.
 * Improved with Binarization to help Tesseract see faint fax text.
 */
class OcrPdfTextExtractor(
    private val tesseractCommand: String = defaultTesseractCommand()
) {

    fun extractLines(file: File): List<PdfLine> {
        return extractLines(file, pageSegMode = 3)
    }

    fun extractLines(file: File, pageSegMode: Int): List<PdfLine> {
        return extractLines(file, pageSegMode = pageSegMode, binarize = true)
    }

    fun extractLines(file: File, pageSegMode: Int, binarize: Boolean): List<PdfLine> {
        return extractLines(file, pageSegMode = pageSegMode, binarize = binarize, dpi = 400f)
    }

    fun extractLines(file: File, pageSegMode: Int, binarize: Boolean, dpi: Float): List<PdfLine> {
        return performOcr(file, pageSegMode, binarize, dpi)
    }

    fun extractLinesWithPdftoppm(file: File, pageSegMode: Int, dpi: Int = 400): List<PdfLine> {
        val pdftoppmCommand = findPdftoppmCommand() ?: return emptyList()
        val allLines = mutableListOf<PdfLine>()
        val tempDir = Files.createTempDirectory("po_parser_pdftoppm_ocr").toFile()

        try {
            val outputPrefix = File(tempDir, "page").absolutePath
            val renderProcess = ProcessBuilder(
                pdftoppmCommand,
                "-r",
                dpi.toString(),
                "-gray",
                "-png",
                file.absolutePath,
                outputPrefix
            )
                .redirectErrorStream(true)
                .start()

            renderProcess.inputStream.bufferedReader().use { it.readText() }
            if (renderProcess.waitFor() != 0) return emptyList()

            tempDir.listFiles { candidate ->
                candidate.isFile && candidate.extension.equals("png", ignoreCase = true)
            }
                ?.sortedBy { it.name }
                ?.forEachIndexed { index, rendered ->
                    /*
                     * Some Tesseract builds are fussy about pdftoppm's hyphenated
                     * output names in temp directories. Copy to a plain filename.
                     */
                    val imageFile = File(tempDir, "ocr_page_${index}.png")
                    rendered.copyTo(imageFile, overwrite = true)

                    val pageText = runTesseract(imageFile, pageSegMode)
                    pageText.lines()
                        .filter { it.isNotBlank() }
                        .forEach { allLines.add(PdfLine(tokens = emptyList(), text = it.trim())) }
                }
        } finally {
            tempDir.deleteRecursively()
        }

        return allLines
    }

    private fun performOcr(file: File, pageSegMode: Int, binarize: Boolean, dpi: Float): List<PdfLine> {
        val allLines = mutableListOf<PdfLine>()
        val tempDir = Files.createTempDirectory("po_parser_ocr").toFile()

        try {
            Loader.loadPDF(file).use { document ->
                val renderer = PDFRenderer(document)
                for (i in 0 until document.numberOfPages) {
                    // 1. Render at 400 DPI. Grayscale is better for binarization, while
                    // RGB preserves faint fax header details for non-binarized rescue OCR.
                    val imageType = if (binarize) ImageType.GRAY else ImageType.RGB
                    val sourceImage = renderer.renderImageWithDPI(i, dpi, imageType)

                    // 2. Normalize rotation
                    var processedImage = if (sourceImage.width > sourceImage.height) {
                        rotateImageClockwise90(sourceImage)
                    } else {
                        sourceImage
                    }

                    // 3. Apply Binarization (Thresholding) to strip fax noise when useful.
                    // Some Fisher fax headers lose digits after hard thresholding, so rescue
                    // passes can keep the grayscale render.
                    if (binarize) {
                        processedImage = binarizeImage(processedImage)
                    }

                    val tempImageFile = File(tempDir, "page_$i.png")
                    ImageIO.write(processedImage, "png", tempImageFile)

                    val pageText = runTesseract(tempImageFile, pageSegMode)
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

    private fun runTesseract(inputFile: File, pageSegMode: Int): String {
        val process = ProcessBuilder(
            tesseractCommand,
            inputFile.absolutePath,
            "stdout",
            "--psm",
            pageSegMode.toString(),
            "quiet"
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output
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

        private fun findPdftoppmCommand(): String? {
            val os = System.getProperty("os.name").lowercase()
            val userDir = System.getProperty("user.dir")
            val packagedResourcesDir = System.getProperty("compose.application.resources.dir")

            val candidates = buildList {
                if (!packagedResourcesDir.isNullOrBlank()) {
                    add(File(packagedResourcesDir, "bin/${if (os.contains("win")) "pdftoppm.exe" else "pdftoppm"}"))
                    add(File(packagedResourcesDir, "windows/bin/pdftoppm.exe"))
                    add(File(packagedResourcesDir, "common/bin/${if (os.contains("win")) "pdftoppm.exe" else "pdftoppm"}"))
                }

                if (os.contains("win")) {
                    addAll(listOf(
                    File(userDir, "pdftoppm.exe"),
                    File(userDir, "bin/pdftoppm.exe"),
                    File(userDir, "app/pdftoppm.exe"),
                    File(userDir, "app/bin/pdftoppm.exe"),
                    File(userDir, "resources/bin/pdftoppm.exe"),
                    File(userDir, "app/resources/bin/pdftoppm.exe"),
                    File(userDir, "poppler/bin/pdftoppm.exe"),
                    File(userDir, "app/poppler/bin/pdftoppm.exe"),
                    File("pdftoppm.exe"),
                    File("bin/pdftoppm.exe"),
                    File("C:/Program Files/poppler/Library/bin/pdftoppm.exe"),
                    File("C:/Program Files/poppler/bin/pdftoppm.exe")
                    ))
                } else {
                    val home = System.getProperty("user.home")
                    addAll(listOf(
                    File("/opt/homebrew/bin/pdftoppm"),
                    File("/usr/local/bin/pdftoppm"),
                    File("/opt/local/bin/pdftoppm"),
                    File("/usr/bin/pdftoppm"),
                    File("$home/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/pdftoppm")
                    ))
                }
            }

            candidates.firstOrNull { it.exists() }?.absolutePath?.let { return it }

            return System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .asSequence()
                .map { File(it, if (os.contains("win")) "pdftoppm.exe" else "pdftoppm") }
                .firstOrNull { it.exists() && it.canExecute() }
                ?.absolutePath
        }
    }
}
