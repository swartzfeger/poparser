package com.jay.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
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

            val tempDir = Files.createTempDirectory("po-parser-ocr").toFile()
            try {
                for (pageIndex in 0 until document.numberOfPages) {
                    val image = renderer.renderImageWithDPI(pageIndex, 300f, ImageType.RGB)

                    val rawText = runTesseractOnImage(
                        image = image,
                        tempDir = tempDir,
                        pageIndex = pageIndex
                    )

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
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private fun runTesseractOnImage(
        image: BufferedImage,
        tempDir: File,
        pageIndex: Int
    ): String {
        val inputFile = File(tempDir, "page-${pageIndex + 1}.png")
        val outputBase = File(tempDir, "page-${pageIndex + 1}")

        ImageIO.write(image, "png", inputFile)

        val exeFile = File(tesseractCommand)
        val tessdataDir = exeFile.parentFile?.let { File(it, "tessdata") }

        val command = mutableListOf(
            tesseractCommand,
            inputFile.absolutePath,
            outputBase.absolutePath,
            "-l",
            "eng",
            "--psm",
            "6"
        )

        if (tessdataDir != null && tessdataDir.exists()) {
            command.add("--tessdata-dir")
            command.add(tessdataDir.absolutePath)
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val processOutput = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException(
                "Tesseract OCR failed on page ${pageIndex + 1}. Exit code: $exitCode. Output: $processOutput"
            )
        }

        val txtFile = File("${outputBase.absolutePath}.txt")
        if (!txtFile.exists()) {
            throw IllegalStateException(
                "Tesseract OCR did not produce output text file for page ${pageIndex + 1}"
            )
        }

        return txtFile.readText(detectCharset())
    }

    private fun detectCharset(): Charset {
        return Charsets.UTF_8
    }

    companion object {
        private fun defaultTesseractCommand(): String {
            val os = System.getProperty("os.name").lowercase()
            val appDir = File(System.getProperty("user.dir"))

            if (os.contains("win")) {
                val bundled = File(appDir, "ocr/tesseract.exe")
                if (bundled.exists()) return bundled.absolutePath
                return "tesseract.exe"
            }

            val bundled = File(appDir, "ocr/tesseract")
            if (bundled.exists()) return bundled.absolutePath

            return "tesseract"
        }
    }
}