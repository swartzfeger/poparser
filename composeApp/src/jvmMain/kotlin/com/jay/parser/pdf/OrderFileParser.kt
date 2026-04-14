package com.jay.parser.parser

import com.jay.parser.pdf.OcrPdfTextExtractor
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfTextExtractor
import com.jay.parser.pdf.PdfLine
import java.io.File

class OrderFileParser(
    private val pdfTextExtractor: PdfTextExtractor = PdfTextExtractor(),
    private val ocrPdfTextExtractor: OcrPdfTextExtractor = OcrPdfTextExtractor(),
    private val pdfFieldParser: PdfFieldParser = PdfFieldParser(),
    private val precisionEuropeExcelParser: PrecisionEuropeExcelParser = PrecisionEuropeExcelParser(),
    private val flowChemExcelParser: FlowChemExcelParser = FlowChemExcelParser()
) {

    fun parse(file: File): List<ParsedPdfFields> {
        return when (file.extension.lowercase()) {
            "pdf" -> parsePdf(file)
            "xlsx" -> parseExcel(file)
            else -> error("Unsupported file type: ${file.extension}")
        }
    }

    private fun parsePdf(file: File): List<ParsedPdfFields> {
        val extractedLines = pdfTextExtractor.extractLines(file)
        val joined = extractedLines.joinToString("\n") { it.text }

        val isFisher = joined.contains("FISHER", true) ||
                file.name.contains("FAX", true) ||
                file.name.matches(Regex("""\d{6,}\.pdf"""))

        // CRITICAL FIX: Do NOT force OCR on clean digital PDFs.
        // If it has plenty of text, let native extraction do its job.
        val needsOcr = extractedLines.size < 15 || joined.length < 500 || file.name.contains("FAX", true)

        val linesToProcess = if (needsOcr) {
            ocrPdfTextExtractor.extractLines(file)
        } else {
            extractedLines
        }

        val orderChunks = segmentLines(linesToProcess, isFisher)

        return orderChunks.mapNotNull { chunk ->
            pdfFieldParser.parse(chunk)
        }
    }

    private fun segmentLines(lines: List<PdfLine>, isFisher: Boolean): List<List<PdfLine>> {
        if (lines.isEmpty()) return emptyList()
        if (!isFisher) return listOf(lines)

        val chunks = mutableListOf<MutableList<PdfLine>>()
        var currentChunk = mutableListOf<PdfLine>()

        for (line in lines) {
            val upper = line.text.uppercase()

            val isNewOrderStart = upper == "PURCHASE ORDER" || upper.contains("FISHER SCIENTIFIC ORDER NUMBER")

            if (isNewOrderStart && currentChunk.size > 15) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
            }

            currentChunk.add(line)
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        return chunks.filter { it.size > 8 }
    }

    private fun parseExcel(file: File): List<ParsedPdfFields> {
        val result = when {
            precisionEuropeExcelParser.canParse(file) -> precisionEuropeExcelParser.parse(file)
            flowChemExcelParser.canParse(file) -> flowChemExcelParser.parse(file)
            else -> error("Unsupported XLSX format: ${file.extension}")
        }
        return listOf(result)
    }
}