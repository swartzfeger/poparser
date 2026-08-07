package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.TableIterator
import java.io.File
import java.io.FileInputStream

class TechnosWordParser {
    fun parse(file: File): ParsedPdfFields {
        require(file.extension.equals("doc", ignoreCase = true)) {
            "Unsupported Word file type: ${file.extension}"
        }

        return FileInputStream(file).use { input ->
            HWPFDocument(input).use { document ->
                parseExtracted(
                    documentText = document.range.text(),
                    tableRows = extractTableRows(document)
                )
            }
        }
    }

    internal fun parseExtracted(
        documentText: String,
        tableRows: List<List<String>>
    ): ParsedPdfFields {
        val normalizedText = cleanText(documentText)
        require(isTechnosDocument(normalizedText)) {
            "Unsupported DOC format: expected a TECHNOS purchase order"
        }

        return ParsedPdfFields(
            customerName = "TECHNOS",
            orderNumber = ORDER_NUMBER_PATTERN.find(normalizedText)?.groupValues?.get(1),
            shipToCustomer = "Technos Pty. Ltd.",
            addressLine1 = "71 McClure Street",
            addressLine2 = "Australia",
            city = "Thornbury",
            state = "VIC",
            zip = "3071",
            items = parseItems(tableRows)
        )
    }

    private fun extractTableRows(document: HWPFDocument): List<List<String>> = buildList {
        val tables = TableIterator(document.range)
        while (tables.hasNext()) {
            val table = tables.next()
            for (rowIndex in 0 until table.numRows()) {
                val row = table.getRow(rowIndex)
                add(
                    (0 until row.numCells()).map { cellIndex ->
                        cleanCell(row.getCell(cellIndex).text())
                    }
                )
            }
        }
    }

    private fun parseItems(rows: List<List<String>>): List<ParsedPdfItem> {
        val headerIndex = rows.indexOfFirst(::isItemHeader)
        if (headerIndex == -1) return emptyList()

        return rows.drop(headerIndex + 1).mapNotNull { cells ->
            if (cells.size < 4) return@mapNotNull null

            val rawSku = cells[1]
            val sku = normalizeSku(rawSku) ?: return@mapNotNull null
            val poDescription = cells[2].trim()
            val quantity = QUANTITY_PATTERN.find(cells.last())
                ?.groupValues
                ?.get(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()
                ?: return@mapNotNull null
            val unitPrice = PRICE_PATTERN.findAll(poDescription)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()

            ParsedPdfItem(
                sku = sku,
                description = ItemMapper.getItemDescription(sku).ifBlank {
                    poDescription.ifBlank { sku }
                },
                quantity = quantity,
                unitPrice = unitPrice
            )
        }
    }

    private fun isItemHeader(cells: List<String>): Boolean {
        val text = compact(cells.joinToString(" "))
        return text.contains("OURCODE") &&
                text.contains("YOURCODE") &&
                text.contains("DESCRIPTIONPRICING") &&
                text.contains("QTYREQUIRED")
    }

    private fun normalizeSku(raw: String): String? {
        val normalized = raw.uppercase().replace(Regex("""\s+"""), "").trim()
        if (normalized.isBlank() || normalized.contains("YOURCODE")) return null

        return when (normalized) {
            "166-144-100" -> "166-144V-100"
            "AD160" -> "115-12V-100"
            "JD169-12V-100" -> "169-12V-100"
            else -> normalized
        }
    }

    private fun isTechnosDocument(text: String): Boolean {
        val compact = compact(text)
        return compact.contains("PURCHASEORDERTOPRECISIONLABORATORIES") &&
                compact.contains("TECHNOSPTYLTD") &&
                compact.contains("TECHNOSCOMAU")
    }

    private fun cleanText(value: String): String = value
        .replace('\u0007', ' ')
        .replace('\r', ' ')
        .replace('\u000B', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun cleanCell(value: String): String = cleanText(value)

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex(
            """PURCHASE\s+ORDER\s+NO\s+(\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        val QUANTITY_PATTERN = Regex("""^\s*([\d,]+(?:\.\d+)?)""")
        val PRICE_PATTERN = Regex(
            """(?:@\s*(?:US\s*)?\$?\s*|\$\s*)([\d,]+(?:\.\d{1,3})?)""",
            RegexOption.IGNORE_CASE
        )
    }
}
