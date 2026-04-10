package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfTextExtractor
import java.io.File

class OrderFileParser(
    private val pdfTextExtractor: PdfTextExtractor = PdfTextExtractor(),
    private val pdfFieldParser: PdfFieldParser = PdfFieldParser(),
    private val precisionEuropeExcelParser: PrecisionEuropeExcelParser = PrecisionEuropeExcelParser()
) {

    fun parse(file: File): ParsedPdfFields {
        return when (file.extension.lowercase()) {
            "pdf" -> {
                val lines = pdfTextExtractor.extractLines(file)
                pdfFieldParser.parse(lines)
            }

            "xlsx" -> {
                if (precisionEuropeExcelParser.canParse(file)) {
                    precisionEuropeExcelParser.parse(file)
                } else {
                    error("Unsupported XLSX format: ${file.name}")
                }
            }

            else -> error("Unsupported file type: ${file.extension}")
        }
    }
}