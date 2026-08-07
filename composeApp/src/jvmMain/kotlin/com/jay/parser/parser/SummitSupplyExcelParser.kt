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

class SummitSupplyExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheet("Sheet1") ?: return false
                    sheet.cellText(0, 0).equals("PRECISION LABORATORIES", ignoreCase = true) &&
                            sheet.cellText(6, 0).equals("PO:", ignoreCase = true) &&
                            sheet.cellText(8, 1).equals("SUMMIT SUPPLY", ignoreCase = true) &&
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
                val sheet = workbook.getSheet("Sheet1")
                    ?: error("Missing Sheet1 in ${file.name}")
                val headerRow = findItemHeader(sheet)
                    ?: error("Missing Summit Supply item header in ${file.name}")
                val customer = CustomerMapper.lookupCustomer(CUSTOMER_NAME)
                val address = parseCityStateZip(sheet.cellText(10, 1))

                val items = buildList {
                    for (rowIndex in (headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val quantity = row.numericOrNull(0) ?: continue
                        val rawSku = row.text(1)
                        val unitPrice = row.numericOrNull(3) ?: continue
                        val sku = normalizeSku(rawSku) ?: continue
                        val poDescription = row.text(2)

                        add(
                            ParsedPdfItem(
                                sku = sku,
                                description = ItemMapper.getItemDescription(sku).ifBlank {
                                    poDescription.ifBlank { sku }
                                },
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                return ParsedPdfFields(
                    customerName = CUSTOMER_NAME,
                    orderNumber = sheet.cellText(6, 1).ifBlank { null },
                    shipToCustomer = sheet.cellText(8, 1).ifBlank { CUSTOMER_NAME },
                    addressLine1 = sheet.cellText(9, 1).ifBlank { null },
                    city = address.city,
                    state = address.state,
                    zip = address.zip,
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun findItemHeader(sheet: org.apache.poi.ss.usermodel.Sheet): Int? =
        (0..sheet.lastRowNum).firstOrNull { rowIndex ->
            sheet.cellText(rowIndex, 0).equals("QTY", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 1).equals("ITEM #", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 3).equals("UNIT PRICE", ignoreCase = true)
        }

    private fun normalizeSku(raw: String): String? {
        val normalized = raw.uppercase().replace(Regex("""\s+"""), "").trim()
        if (normalized.isBlank()) return null

        return when (normalized) {
            "QAC-400-IV-50" -> "QAC-400-1V-50"
            else -> normalized
        }
    }

    private fun parseCityStateZip(raw: String): AddressParts {
        val match = CITY_STATE_ZIP_PATTERN.find(raw.replace(Regex("""\s+"""), " ").trim())
            ?: return AddressParts(null, null, null)

        return AddressParts(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].uppercase(),
            zip = match.groupValues[3]
        )
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

    private data class AddressParts(
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private companion object {
        const val CUSTOMER_NAME = "SUMMIT SUPPLY"
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
