package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

class RamcoExcelParser {

    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheet("Sheet1") ?: return false
                    sheet.cellText(7, 3).equals("Purchase Order Number", ignoreCase = true) &&
                            sheet.cellText(15, 1).contains("Ramco Manufacturing", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheet("Sheet1")
                    ?: error("Missing Sheet1 in ${file.name}")
                val customerName = "RAMCO"
                val customer = CustomerMapper.lookupCustomer(customerName)
                val headerRow = (0..sheet.lastRowNum).firstOrNull { rowIndex ->
                    sheet.cellText(rowIndex, 0).equals("QTY", ignoreCase = true) &&
                            sheet.cellText(rowIndex, 1).contains("PART NUMBER", ignoreCase = true)
                } ?: error("Missing Ramco item header in ${file.name}")

                val items = buildList {
                    for (rowIndex in (headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val quantity = row.numericOrNull(0) ?: continue
                        val rawDescription = row.text(1)
                        val unitPrice = row.numericOrNull(4) ?: continue
                        val sku = skuForDescription(rawDescription) ?: continue

                        add(
                            ParsedPdfItem(
                                sku = sku,
                                description = ItemMapper.getItemDescription(sku).ifBlank { rawDescription },
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                return ParsedPdfFields(
                    customerName = customerName,
                    orderNumber = sheet.cellText(8, 3).ifBlank { null },
                    shipToCustomer = "RAMCO MANUFACTURING COMPANY INC.",
                    addressLine1 = "5634 HOGUE ST",
                    addressLine2 = null,
                    city = "HOUSTON",
                    state = "TX",
                    zip = "77087",
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun skuForDescription(description: String): String? {
        val normalized = description.uppercase().replace(Regex("""\s+"""), " ").trim()
        return when {
            normalized.contains("UNIVERSAL TEST PAPERS") &&
                    normalized.contains("1.5 X 3") &&
                    normalized.contains("1000") -> "210-1000-1.53"

            normalized.contains("UNIVERSAL TEST PAPERS") &&
                    normalized.contains("1.5\" X 25\"") &&
                    normalized.contains("100 PACK") -> "210-100-1525"

            else -> null
        }
    }

    private fun org.apache.poi.ss.usermodel.Sheet.cellText(rowIndex: Int, columnIndex: Int): String =
        getRow(rowIndex)?.text(columnIndex).orEmpty()

    private fun Row.text(columnIndex: Int): String {
        val cell = getCell(columnIndex) ?: return ""
        return DataFormatter().formatCellValue(cell).trim()
    }

    private fun Row.numericOrNull(columnIndex: Int): Double? {
        val cell = getCell(columnIndex) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().replace(",", "").toDoubleOrNull()
            CellType.FORMULA -> runCatching { cell.numericCellValue }.getOrNull()
            else -> null
        }
    }
}
