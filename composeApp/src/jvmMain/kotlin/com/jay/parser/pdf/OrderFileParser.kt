package com.jay.parser.parser

import com.jay.parser.pdf.OcrPdfTextExtractor
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfTextExtractor
import java.io.File

class OrderFileParser(
    private val pdfTextExtractor: PdfTextExtractor = PdfTextExtractor(),
    private val ocrPdfTextExtractor: OcrPdfTextExtractor = OcrPdfTextExtractor(),
    private val pdfFieldParser: PdfFieldParser = PdfFieldParser(),
    private val precisionEuropeExcelParser: PrecisionEuropeExcelParser = PrecisionEuropeExcelParser(),
    private val flowChemExcelParser: FlowChemExcelParser = FlowChemExcelParser()
) {

    fun parse(file: File): ParsedPdfFields {
        return when (file.extension.lowercase()) {
            "pdf" -> {
                val extractedLines = pdfTextExtractor.extractLines(file)
                val hasUsableText = extractedLines.any { it.text.isNotBlank() }

                val linesToParse = if (hasUsableText) {
                    extractedLines
                } else {
                    println("OCR fallback triggered for ${file.name}")
                    ocrPdfTextExtractor.extractLines(file)
                }

                pdfFieldParser.parse(linesToParse)
            }

            "xlsx" -> when {
                precisionEuropeExcelParser.canParse(file) -> precisionEuropeExcelParser.parse(file)
                flowChemExcelParser.canParse(file) -> flowChemExcelParser.parse(file)
                else -> error("Unsupported XLSX format: ${file.name}")
            }

            else -> error("Unsupported file type: ${file.extension}")
        }
    }
}