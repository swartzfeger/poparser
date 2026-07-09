package com.jay.parser.parser

import com.jay.parser.pdf.OcrPdfTextExtractor
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfLine
import com.jay.parser.pdf.PdfTextExtractor
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
        println("DEBUG: I am definitely running the code in the /parser folder!")

        val extractedLines = pdfTextExtractor.extractLines(file)
        val joinedNative = extractedLines.joinToString("\n") { it.text }
        val nativeLooksCorrupted = looksLikeCorruptedTextLayer(extractedLines)

        val preOcrCandidateLines = if (
            nativeLooksCorrupted ||
            joinedNative.contains("FISHER", true) ||
            file.name.contains("FAX", true) ||
            file.nameWithoutExtension.matches(Regex("""\d{6,8}"""))
        ) {
            ocrPdfTextExtractor.extractLines(file)
        } else {
            emptyList()
        }

        val joinedOcrCandidate = preOcrCandidateLines.joinToString("\n") { it.text }

        val isChosun = joinedNative.contains("CHOSUN MEASUREMENT", true) ||
                joinedOcrCandidate.contains("CHOSUN MEASUREMENT", true) ||
                file.name.contains("CHOSUN", true)

        val isCharlotte = joinedNative.contains("CHARLOTTE PRODUCTS", true) ||
                joinedOcrCandidate.contains("CHARLOTTE PRODUCTS", true) ||
                file.name.contains("PRELAB", true)

        val isFisher = !isChosun && !isCharlotte && (
                joinedNative.contains("FISHER SCIENTIFIC", true) ||
                        joinedNative.contains("FISHER HEALTHCARE", true) ||
                        joinedOcrCandidate.contains("FISHER SCIENTIFIC", true) ||
                        joinedOcrCandidate.contains("FISHER SCTENTIF", true) ||
                        joinedOcrCandidate.contains("FISHER SCLANTIFIC", true) ||
                        file.name.contains("FAX", true)
                )

        val useAquaOcrOnly = shouldUseOcrForAquaPhoenixOnly(
            textLines = extractedLines,
            ocrLines = preOcrCandidateLines
        )

        val useCovidienOcrOnly = shouldUseOcrForCovidienOnly(
            textLines = extractedLines,
            ocrLines = preOcrCandidateLines
        )

        val linesToProcess = when {
            isFisher -> {
                if (preOcrCandidateLines.isNotEmpty()) {
                    preOcrCandidateLines
                } else {
                    ocrPdfTextExtractor.extractLines(file)
                }
            }

            useAquaOcrOnly -> {
                println("DEBUG: Using OCR lines for Aqua Phoenix due to corrupted embedded text layer")
                preOcrCandidateLines
            }

            useCovidienOcrOnly -> {
                println("DEBUG: Using OCR lines for Covidien due to corrupted embedded text layer")
                preOcrCandidateLines
            }

            extractedLines.size < 3 -> ocrPdfTextExtractor.extractLines(file)

            else -> extractedLines
        }

        val orderChunks = segmentLines(linesToProcess, isFisher)

        val parsedOrders = orderChunks.map { chunk ->
            pdfFieldParser.parse(chunk)
        }

        if (isFisher) {
            val nativePrs = (joinedNative + "\n" + joinedOcrCandidate)
                .uppercase()
                .replace("FK", "PR")
                .replace("FR", "PR")
                .replace("PK", "PR")
                .replace(Regex("""PR\s*41\s+(\d{4,5})"""), "PR41$1")
                .let { normalized ->
                    Regex("""\bPR\d{7,8}\b""")
                        .findAll(normalized)
                        .map { it.value }
                        .distinct()
                        .toList()
                }

            /*
             * Only use positional PR recovery when the counts line up. In multi-order
             * Fisher files, the first page sometimes has no recoverable PR while later
             * pages do. Blind positional assignment causes the first order to steal the
             * second order's PR number.
             */
            val ordersAfterNativeRecovery = if (
                nativePrs.isNotEmpty() &&
                parsedOrders.count { it.orderNumber.isNullOrBlank() } == nativePrs.size &&
                parsedOrders.size == nativePrs.size
            ) {
                parsedOrders.mapIndexed { index, order ->
                    if (order.orderNumber.isNullOrBlank() && index < nativePrs.size) {
                        order.copy(orderNumber = nativePrs[index])
                    } else {
                        order
                    }
                }
            } else {
                parsedOrders
            }

            val filenamePr = if (file.nameWithoutExtension.matches(Regex("""\d{6,8}"""))) {
                "PR${file.nameWithoutExtension}"
            } else {
                null
            }

            return ordersAfterNativeRecovery.mapIndexed { index, order ->
                if (order.orderNumber.isNullOrBlank() && filenamePr != null && index == 0) {
                    order.copy(orderNumber = filenamePr)
                } else {
                    order
                }
            }
        }

        return parsedOrders
    }

    private fun looksLikeCorruptedTextLayer(lines: List<PdfLine>): Boolean {
        val text = lines.joinToString("") { it.text }
        if (text.isBlank()) return false

        val privateUseCount = text.count { it.code in 0xE000..0xF8FF }
        val asciiLetterOrDigitCount = text.count { it.isLetterOrDigit() && it.code < 128 }
        val totalNonWhitespace = text.count { !it.isWhitespace() }

        return privateUseCount > asciiLetterOrDigitCount ||
                (totalNonWhitespace > 0 && asciiLetterOrDigitCount < totalNonWhitespace / 2)
    }

    private fun shouldUseOcrForAquaPhoenixOnly(
        textLines: List<PdfLine>,
        ocrLines: List<PdfLine>
    ): Boolean {
        if (ocrLines.isEmpty()) return false

        val nativeText = textLines.joinToString("") { it.text }
        val ocrText = ocrLines.joinToString(" ") { it.text }.uppercase()

        val looksLikeAqua =
            ocrText.contains("AQUAPHOENIX") ||
                    ocrText.contains("AQUA PHOENIX") ||
                    ocrText.contains("AQUAPHOENIX SCIENTIFIC") ||
                    ocrText.contains("AQUA PHOENIX SCIENTIFIC") ||
                    ocrText.contains("PURCHASE ORDER - PO26030078")

        if (!looksLikeAqua) return false

        val privateUseCount = nativeText.count { it.code in 0xE000..0xF8FF }
        val asciiLetterOrDigitCount = nativeText.count { it.isLetterOrDigit() && it.code < 128 }
        val totalNonWhitespace = nativeText.count { !it.isWhitespace() }

        return privateUseCount > asciiLetterOrDigitCount ||
                (totalNonWhitespace > 0 && asciiLetterOrDigitCount < totalNonWhitespace / 2)
    }

    private fun shouldUseOcrForCovidienOnly(
        textLines: List<PdfLine>,
        ocrLines: List<PdfLine>
    ): Boolean {
        if (ocrLines.isEmpty()) return false

        val nativeText = textLines.joinToString("") { it.text }
        val ocrText = ocrLines.joinToString(" ") { it.text }.uppercase()

        val looksLikeCovidien =
            ocrText.contains("COVIDIEN") ||
                    ocrText.contains("MEDTRONIC") ||
                    ocrText.contains("PURCHASE ORDER NUMBER:") ||
                    ocrText.contains("VND ITEM: 290-1-1515") ||
                    ocrText.contains("SURGICAL SOLUTIONS")

        if (!looksLikeCovidien) return false

        val asciiLetterOrDigitCount = nativeText.count { it.isLetterOrDigit() && it.code < 128 }
        val totalNonWhitespace = nativeText.count { !it.isWhitespace() }

        return totalNonWhitespace > 0 && asciiLetterOrDigitCount < totalNonWhitespace / 2
    }

    private fun segmentLines(lines: List<PdfLine>, isFisher: Boolean): List<List<PdfLine>> {
        if (lines.isEmpty()) return emptyList()
        if (!isFisher) return listOf(lines)

        val chunks = mutableListOf<MutableList<PdfLine>>()
        var currentChunk = mutableListOf<PdfLine>()
        var lastSplitIndex = -1000

        for ((index, line) in lines.withIndex()) {
            val upper = line.text.uppercase().trim()

            val isHeaderLine =
                upper.contains("FISHER SCIENTIFIC COMPANY") &&
                        upper.length < 120

            val currentHasOrderContent = currentChunk.any {
                val t = it.text.uppercase()
                t.contains("FISHER PO LINE") ||
                        t.contains("PISHER FPO LINE") ||
                        t.contains("TOTAL:") ||
                        t.contains("TOTAL -") ||
                        t.contains("SUPPLIER") ||
                        t.contains("CATALOG")
            }

            val isRealPageBreak =
                isHeaderLine &&
                        currentChunk.size >= 20 &&
                        currentHasOrderContent &&
                        (index - lastSplitIndex > 12)

            if (isRealPageBreak) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                lastSplitIndex = index
            }

            currentChunk.add(line)
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        return chunks.filter { chunk ->
            chunk.size > 8 &&
                    chunk.any { it.text.contains("FISHER", true) } &&
                    chunk.any {
                        it.text.contains("SUPPLIER", true) ||
                                it.text.contains("CATALOG", true) ||
                                it.text.contains("FISHER PO LINE", true) ||
                                it.text.contains("PISHER FPO LINE", true) ||
                                it.text.contains("TOTAL", true)
                    }
        }
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
