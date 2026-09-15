package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

class ChemMarkExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    (0 until workbook.numberOfSheets)
                        .map(workbook::getSheetAt)
                        .any { sheet -> isChemMarkSheet(sheet) && findItemColumns(sheet) != null }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = (0 until workbook.numberOfSheets)
                    .map(workbook::getSheetAt)
                    .firstOrNull { isChemMarkSheet(it) && findItemColumns(it) != null }
                    ?: error("Missing Chem Mark purchase-order sheet in ${file.name}")
                val columns = findItemColumns(sheet)
                    ?: error("Missing Chem Mark item header in ${file.name}")
                val shipTo = parseShipTo(sheet)
                    ?: error("Missing Chem Mark ship-to address in ${file.name}")
                val customerId = customerIdFor(shipTo.customer)
                    ?: error("Unsupported Chem Mark ship-to location: ${shipTo.customer}")
                val customer = CustomerMapper.lookupCustomer(customerId)

                val items = buildList {
                    for (rowIndex in (columns.headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val sku = normalizeSku(row.text(columns.sku)) ?: continue
                        val quantity = row.numericOrNull(columns.quantity) ?: continue
                        val unitPrice = row.numericOrNull(columns.unitPrice) ?: continue
                        val extension = row.numericOrNull(columns.total) ?: continue
                        if (quantity <= 0.0 || unitPrice <= 0.0 ||
                            abs(quantity * unitPrice - extension) > EXTENSION_TOLERANCE
                        ) {
                            continue
                        }

                        add(
                            ParsedPdfItem(
                                sku = sku,
                                description = ItemMapper.getItemDescription(sku).ifBlank {
                                    row.text(columns.description).ifBlank { sku }
                                },
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                return ParsedPdfFields(
                    customerName = customerId,
                    orderNumber = findOrderNumber(sheet),
                    shipToCustomer = shipTo.customer,
                    addressLine1 = shipTo.addressLine1,
                    addressLine2 = shipTo.attention,
                    city = shipTo.city,
                    state = shipTo.state,
                    zip = shipTo.zip,
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun isChemMarkSheet(sheet: Sheet): Boolean {
        val text = sheetText(sheet)
        return text.contains("CHEMMARK") &&
                text.contains("PURCHASEORDER") &&
                text.contains("SHIPTO")
    }

    private fun findOrderNumber(sheet: Sheet): String? {
        for (rowIndex in 0..minOf(sheet.lastRowNum, 15)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val lastColumn = row.lastCellNum.coerceAtLeast(0).toInt()
            for (columnIndex in 0 until lastColumn) {
                if (compact(row.text(columnIndex)) != "PO") continue
                for (candidateIndex in (columnIndex + 1) until lastColumn) {
                    val candidate = row.text(candidateIndex).trim()
                    if (ORDER_NUMBER_PATTERN.matches(candidate)) return candidate
                }
            }
        }
        return null
    }

    private fun parseShipTo(sheet: Sheet): ShipTo? {
        var headerRow = -1
        var column = -1
        for (rowIndex in 0..minOf(sheet.lastRowNum, 20)) {
            val row = sheet.getRow(rowIndex) ?: continue
            for (columnIndex in 0 until row.lastCellNum.coerceAtLeast(0).toInt()) {
                if (compact(row.text(columnIndex)) == "SHIPTO") {
                    headerRow = rowIndex
                    column = columnIndex
                    break
                }
            }
            if (headerRow >= 0) break
        }
        if (headerRow < 0) return null

        val values = ((headerRow + 1)..minOf(sheet.lastRowNum, headerRow + 8))
            .map { rowIndex -> sheet.cellText(rowIndex, column) }
            .filter(String::isNotBlank)
        val customerIndex = values.indexOfFirst { compact(it).contains("CHEMMARK") }
        if (customerIndex < 0) return null

        val customer = values[customerIndex]
        val attention = values.take(customerIndex)
            .firstOrNull()
            ?.let { "ATTN: $it" }
        val addressLine1 = values.drop(customerIndex + 1)
            .firstOrNull { STREET_PATTERN.matches(it) }
            ?: return null
        val cityMatch = values.drop(customerIndex + 1)
            .firstNotNullOfOrNull(CITY_STATE_ZIP_PATTERN::find)
            ?: return null

        return ShipTo(
            customer = customer,
            addressLine1 = addressLine1,
            attention = attention,
            city = cityMatch.groupValues[1].trim(),
            state = cityMatch.groupValues[2].uppercase(),
            zip = cityMatch.groupValues[3]
        )
    }

    private fun customerIdFor(shipToCustomer: String): String? {
        val value = compact(shipToCustomer)
        return when {
            value.contains("EASTTEXAS") -> EAST_TEXAS_CUSTOMER_ID
            value.contains("SANANTONIO") -> SAN_ANTONIO_CUSTOMER_ID
            else -> null
        }
    }

    private fun findItemColumns(sheet: Sheet): ItemColumns? {
        for (rowIndex in 0..minOf(sheet.lastRowNum, 30)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val headings = (0 until row.lastCellNum.coerceAtLeast(0).toInt())
                .associateWith { compact(row.text(it)) }
            val sku = headings.entries.firstOrNull { it.value == "ITEM" }?.key ?: continue
            val description = headings.entries.firstOrNull { it.value == "DESCRIPTION" }?.key ?: continue
            val quantity = headings.entries.firstOrNull { it.value == "QTY" }?.key ?: continue
            val unitPrice = headings.entries.firstOrNull { it.value == "UNITPRICE" }?.key ?: continue
            val total = headings.entries.firstOrNull { it.value == "TOTAL" }?.key ?: continue
            return ItemColumns(rowIndex, sku, description, quantity, unitPrice, total)
        }
        return null
    }

    private fun sheetText(sheet: Sheet): String = (0..minOf(sheet.lastRowNum, 25))
        .joinToString("") { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@joinToString ""
            (0 until row.lastCellNum.coerceAtLeast(0).toInt())
                .joinToString("") { columnIndex -> compact(row.text(columnIndex)) }
        }

    private fun normalizeSku(raw: String): String? = raw
        .uppercase()
        .replace(Regex("""\s+"""), "")
        .trim()
        .takeIf(String::isNotBlank)

    private fun Sheet.cellText(rowIndex: Int, columnIndex: Int): String =
        getRow(rowIndex)?.text(columnIndex).orEmpty()

    private fun Row.text(columnIndex: Int): String {
        val cell = getCell(columnIndex) ?: return ""
        return DataFormatter().formatCellValue(cell).trim()
    }

    private fun Row.numericOrNull(columnIndex: Int): Double? {
        val cell = getCell(columnIndex) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().removePrefix("$").replace(",", "").toDoubleOrNull()
            CellType.FORMULA -> runCatching { cell.numericCellValue }.getOrNull()
            else -> null
        }
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ItemColumns(
        val headerRow: Int,
        val sku: Int,
        val description: Int,
        val quantity: Int,
        val unitPrice: Int,
        val total: Int
    )

    private data class ShipTo(
        val customer: String,
        val addressLine1: String,
        val attention: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private companion object {
        const val EAST_TEXAS_CUSTOMER_ID = "CHEM MARK OF EAST TE"
        const val SAN_ANTONIO_CUSTOMER_ID = "CHEM MARK of SAN ANT"
        const val EXTENSION_TOLERANCE = 0.02
        val ORDER_NUMBER_PATTERN = Regex("""\d{5,12}""")
        val STREET_PATTERN = Regex("""\d+\s+.+""")
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
