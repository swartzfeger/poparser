package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

class ProducePackagingWordParser {
    fun parse(file: File): ParsedPdfFields {
        require(file.extension.equals("docx", ignoreCase = true)) {
            "Unsupported Word file type: ${file.extension}"
        }

        return FileInputStream(file).use { input ->
            XWPFDocument(input).use { document ->
                parseExtracted(
                    documentText = document.paragraphs.joinToString("\n") { it.text },
                    tableRows = document.tables.flatMap { table ->
                        table.rows.map { row -> row.tableCells.map { cell -> cleanCell(cell.text) } }
                    }
                )
            }
        }
    }

    internal fun parseExtracted(
        documentText: String,
        tableRows: List<List<String>>
    ): ParsedPdfFields {
        val searchableText = buildString {
            appendLine(documentText)
            tableRows.forEach { row -> appendLine(row.joinToString(" ")) }
        }
        require(isProducePackagingDocument(searchableText)) {
            "Unsupported DOCX format: expected a PRODUCE PACKAGING purchase order"
        }

        val shipToCustomer = findValueAfterLabel(tableRows, "SHIP TO")
            ?.takeIf { it.isNotBlank() }
            ?: CUSTOMER_NAME

        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = findValueAfterLabel(tableRows, "PO NUMBER")
                ?.let(ORDER_NUMBER_PATTERN::find)
                ?.value,
            shipToCustomer = shipToCustomer,
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            state = null,
            zip = null,
            terms = findValueAfterLabel(tableRows, "PAYMENT TERMS")?.ifBlank { null },
            items = parseItems(tableRows)
        )
    }

    private fun parseItems(rows: List<List<String>>): List<ParsedPdfItem> {
        val headerIndex = rows.indexOfFirst(::isItemHeader)
        if (headerIndex == -1) return emptyList()

        return rows.drop(headerIndex + 1)
            .takeWhile { row -> row.none { compact(it).startsWith("SUBTOTAL") } }
            .mapNotNull { cells ->
                if (cells.size < 2) return@mapNotNull null

                val itemCell = cells[0]
                val sku = SKU_PATTERN.find(itemCell)
                    ?.value
                    ?.uppercase()
                    ?: return@mapNotNull null
                val quantity = parseNumber(cells[1]) ?: return@mapNotNull null
                val unitPrice = cells.getOrNull(3)?.let(::parseNumber)
                val poDescription = itemCell
                    .removePrefix(sku)
                    .trim()
                    .removePrefix("(")
                    .removeSuffix(")")
                    .trim()

                ParsedPdfItem(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        poDescription.ifBlank { sku }
                    },
                    quantity = quantity,
                    unitPrice = unitPrice,
                    uom = cells.getOrNull(2)?.trim()?.ifBlank { null }
                )
            }
    }

    private fun findValueAfterLabel(rows: List<List<String>>, label: String): String? {
        val normalizedLabel = compact(label)
        rows.forEach { cells ->
            cells.forEachIndexed { index, cell ->
                if (compact(cell) == normalizedLabel) {
                    return cells.getOrNull(index + 1)?.trim()
                }
            }
        }
        return null
    }

    private fun isItemHeader(cells: List<String>): Boolean {
        val normalized = cells.map(::compact)
        return normalized.any { it == "ITEMDESCRIPTION" } &&
                normalized.any { it == "QUANTITY" }
    }

    private fun isProducePackagingDocument(text: String): Boolean {
        val normalized = compact(text)
        return normalized.contains("PURCHASEORDER") &&
                normalized.contains("PRECISIONLABORATORIES") &&
                normalized.contains("PRODUCEPACKAGING")
    }

    private fun parseNumber(value: String): Double? = NUMBER_PATTERN.find(value)
        ?.value
        ?.replace(",", "")
        ?.toDoubleOrNull()

    private fun cleanCell(value: String): String = value
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_NAME = "PRODUCE PACKAGING INC"
        val ORDER_NUMBER_PATTERN = Regex("""\b\d+\b""")
        val SKU_PATTERN = Regex("""\b[A-Z][A-Z0-9]*(?:-[A-Z0-9]+){2,}\b""", RegexOption.IGNORE_CASE)
        val NUMBER_PATTERN = Regex("""-?[\d,]+(?:\.\d+)?""")
    }
}
