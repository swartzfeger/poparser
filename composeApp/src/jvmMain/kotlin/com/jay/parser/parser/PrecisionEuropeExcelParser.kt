package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

class PrecisionEuropeExcelParser {

    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { wb ->
                    val order = wb.getSheet("ORDER") ?: return false
                    val a1 = order.getCellString(0, 0)
                    val d1 = order.getCellString(0, 3)

                    a1.equals("Precision Europe", ignoreCase = true) &&
                            d1.contains("PURCHASE ORDER", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { wb ->
                val order = wb.getSheet("ORDER")
                    ?: error("Missing ORDER sheet in ${file.name}")

                val customerName = "Precision Europe"
                val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

                val orderNumber = order.getCellString(1, 4).ifBlank { null }

                val shipToCustomer = order.getCellString(6, 3).ifBlank {
                    order.getCellString(6, 0)
                }.ifBlank { "Precision Europe" }

                val addressLine1 = order.getCellString(7, 3).ifBlank {
                    order.getCellString(7, 0)
                }.ifBlank { null }

                val addressLine2 = order.getCellString(8, 3).ifBlank {
                    order.getCellString(8, 0)
                }.ifBlank { null }

                val cityStateZipRaw = order.getCellString(9, 3).ifBlank {
                    order.getCellString(9, 0)
                }

                val shipTo = parseUkCityStateZip(cityStateZipRaw)

                val items = mutableListOf<ParsedPdfItem>()

                var rowIndex = 15
                while (true) {
                    val row = order.getRow(rowIndex) ?: break

                    val quantity = row.getNumericOrNull(0)
                    val sku = row.getStringOrBlank(1).trim().uppercase()
                    val description = row.getStringOrBlank(2).trim()
                    val unitPrice = row.getNumericOrNull(4)

                    val isEmptyRow = quantity == null &&
                            sku.isBlank() &&
                            description.isBlank() &&
                            unitPrice == null

                    if (isEmptyRow) break

                    if (quantity != null && sku.isNotBlank() && unitPrice != null) {
                        items.add(
                            ParsedPdfItem(
                                sku = sku,
                                description = description.ifBlank { sku },
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }

                    rowIndex++
                }

                return ParsedPdfFields(
                    customerName = customerName,
                    orderNumber = orderNumber,
                    shipToCustomer = shipToCustomer,
                    addressLine1 = addressLine1,
                    addressLine2 = addressLine2,
                    city = shipTo.city,
                    state = shipTo.state,
                    zip = shipTo.zip,
                    terms = mappedCustomer?.terms,
                    items = items
                )
            }
        }
    }

    private fun parseUkCityStateZip(raw: String): CityStateZip {
        val cleaned = raw.replace(Regex("""\s+"""), " ").trim()

        val match = Regex(
            """^(.+?),\s*(.+?),\s*([A-Z]{1,4}\d[A-Z0-9\s]*)$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim(),
                state = "UK",
                zip = match.groupValues[3].replace(Regex("""\s+"""), " ").trim()
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet.getCellString(rowIndex: Int, colIndex: Int): String {
        val row = getRow(rowIndex) ?: return ""
        val cell = row.getCell(colIndex) ?: return ""

        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toLocalDate().toString()
                } else {
                    val n = cell.numericCellValue
                    if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.toString().trim()
            else -> ""
        }
    }

    private fun Row.getStringOrBlank(colIndex: Int): String {
        val cell = getCell(colIndex) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toLocalDate().toString()
                } else {
                    val n = cell.numericCellValue
                    if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
                }
            }
            CellType.FORMULA -> cell.toString().trim()
            else -> ""
        }
    }

    private fun Row.getNumericOrNull(colIndex: Int): Double? {
        val cell = getCell(colIndex) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().replace(",", "").toDoubleOrNull()
            CellType.FORMULA -> runCatching { cell.numericCellValue }.getOrNull()
            else -> null
        }
    }

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}