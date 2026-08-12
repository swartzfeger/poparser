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

class RilabExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheet("BASE OC LTDA") ?: return false
                    val text = (0..minOf(sheet.lastRowNum, 20))
                        .joinToString(" ") { rowIndex -> sheet.rowText(rowIndex) }
                        .uppercase()

                    text.contains("CARLOS RIVAS PADILLA") &&
                            text.contains("WWW.RILAB.CL") &&
                            text.contains("PURCHASE ORDER") &&
                            findItemHeader(sheet) != null
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheet("BASE OC LTDA")
                    ?: error("Missing BASE OC LTDA in ${file.name}")
                val headerRow = findItemHeader(sheet)
                    ?: error("Missing RILAB item header in ${file.name}")
                val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

                val items = buildList {
                    for (rowIndex in (headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val quantity = row.numericOrNull(1) ?: continue
                        val sku = normalizeRilabSku(row.text(2)) ?: continue
                        val unitPrice = row.numericOrNull(4) ?: continue

                        add(
                            ParsedPdfItem(
                                sku = sku,
                                description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                return ParsedPdfFields(
                    customerName = CUSTOMER_ID,
                    orderNumber = sheet.cellText(2, 5).ifBlank { null },
                    shipToCustomer = SHIP_TO_CUSTOMER,
                    addressLine1 = ADDRESS_LINE_1,
                    addressLine2 = ADDRESS_LINE_2,
                    city = CITY,
                    state = STATE,
                    zip = ZIP,
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun findItemHeader(sheet: Sheet): Int? =
        (0..sheet.lastRowNum).firstOrNull { rowIndex ->
            sheet.cellText(rowIndex, 0).equals("Qty", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 1).equals("Unit", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 2).equals("Code", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 4).contains("Price", ignoreCase = true)
        }

    private fun normalizeRilabSku(raw: String): String? {
        val compact = raw.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
        return when (compact) {
            "CHL1000IV100", "CHL10001V100" -> "CHL-1000-1V-100"
            "CHLASSURE1V50" -> "CHL-ASSURE-1V-50"
            "CHL2001V100" -> "CHL-200-1V-100"
            "CH3001V100", "CHL3001V100" -> "CHL-300-1V-100"
            "QAC15001V100" -> "QAC-1500-1V-100"
            else -> null
        }
    }

    private fun Sheet.cellText(rowIndex: Int, columnIndex: Int): String =
        getRow(rowIndex)?.text(columnIndex).orEmpty()

    private fun Sheet.rowText(rowIndex: Int): String {
        val row = getRow(rowIndex) ?: return ""
        return (0 until row.lastCellNum.coerceAtLeast(0).toInt())
            .joinToString(" ") { columnIndex -> row.text(columnIndex) }
    }

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

    private companion object {
        const val CUSTOMER_ID = "RILAB"
        const val SHIP_TO_CUSTOMER = "Carlos Rivas Padilla y Compañía Limitada"
        const val ADDRESS_LINE_1 = "Av del Valle Sur 570"
        const val ADDRESS_LINE_2 = "Of 102, Ciudad Empresarial, Chile"
        const val CITY = "Huechuraba"
        const val STATE = "RM"
        const val ZIP = "8581151"
    }
}
