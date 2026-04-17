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

        val isFisher = joinedNative.contains("FISHER", true) ||
                file.name.contains("FAX", true) ||
                file.name.matches(Regex("""\d{6,}\.pdf"""))

        val linesToProcess = if (isFisher) {
            ocrPdfTextExtractor.extractLines(file)
        } else if (extractedLines.size < 3) {
            ocrPdfTextExtractor.extractLines(file)
        } else {
            extractedLines
        }

        val orderChunks = segmentLines(linesToProcess, isFisher)
        val parsedOrders = orderChunks.mapNotNull { chunk ->
            pdfFieldParser.parse(chunk)
        }

        if (isFisher) {
            val nativePrs = joinedNative.uppercase()
                .replace("FK", "PR")
                .replace("FR", "PR")
                .replace("PK", "PR")
                .replace(Regex("""PR\s*41\s+(\d{4})"""), "PR41$1")
                .let { normalized ->
                    Regex("""\bPR\d{7,8}\b""")
                        .findAll(normalized)
                        .map { it.value }
                        .distinct()
                        .toList()
                }

            val ordersAfterNativeRecovery = if (nativePrs.isNotEmpty()) {
                parsedOrders.mapIndexed { index, order ->
                    if (order.orderNumber.isNullOrBlank() && index < nativePrs.size) {
                        ParsedPdfFields(
                            customerName = order.customerName,
                            orderNumber = nativePrs[index],
                            shipToCustomer = order.shipToCustomer,
                            addressLine1 = order.addressLine1,
                            addressLine2 = order.addressLine2,
                            city = order.city,
                            state = order.state,
                            zip = order.zip,
                            terms = order.terms,
                            items = order.items
                        )
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

            return ordersAfterNativeRecovery.map { order ->
                if (order.orderNumber.isNullOrBlank() && filenamePr != null) {
                    ParsedPdfFields(
                        customerName = order.customerName,
                        orderNumber = filenamePr,
                        shipToCustomer = order.shipToCustomer,
                        addressLine1 = order.addressLine1,
                        addressLine2 = order.addressLine2,
                        city = order.city,
                        state = order.state,
                        zip = order.zip,
                        terms = order.terms,
                        items = order.items
                    )
                } else {
                    order
                }
            }
        }

        return parsedOrders
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
                        upper.length < 80

            val isRealPageBreak =
                isHeaderLine &&
                        currentChunk.size > 25 &&
                        (index - lastSplitIndex > 25)

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
            chunk.size > 10 &&
                    chunk.any { it.text.contains("FISHER", true) }
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