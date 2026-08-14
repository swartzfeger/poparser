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

class ZhangJiagangSurejiExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.findPurchaseOrderSheet() ?: return false
                    val headerText = (0..minOf(sheet.lastRowNum, 18))
                        .joinToString(" ") { rowIndex -> sheet.rowText(rowIndex) }
                    val identity = compact(headerText)

                    identity.contains("ZHANGJIAGANGSUREJIE") &&
                            identity.contains("PURCHASEORDER") &&
                            identity.contains("ZHANGJIAGANGSUREJI") &&
                            identity.contains("B321324TECHNOLOGICALINNOVATIONPARK") &&
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
                val sheet = workbook.findPurchaseOrderSheet()
                    ?: error("Missing $SHEET_NAME sheet in ${file.name}")
                val headerRow = findItemHeader(sheet)
                    ?: error("Missing Zhang Jiagang Sureji item header in ${file.name}")
                val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
                val street = parseStreet(sheet.cellText(11, 4))
                val cityLine = parseChinaCityLine(sheet.cellText(12, 4))

                val items = buildList {
                    for (rowIndex in (headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val itemNumber = row.numericOrNull(0) ?: continue
                        if (itemNumber <= 0.0) continue
                        val sku = normalizeSku(row.text(1)) ?: continue
                        val quantity = row.numericOrNull(6) ?: continue
                        val unitPrice = row.numericOrNull(7) ?: continue
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
                    customerName = CUSTOMER_ID,
                    orderNumber = sheet.cellText(2, 8).ifBlank { null },
                    shipToCustomer = sheet.cellText(10, 4).ifBlank { customer?.name },
                    addressLine1 = street.addressLine1,
                    addressLine2 = street.addressLine2,
                    city = cityLine.city,
                    state = cityLine.state,
                    zip = cityLine.zip,
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun findItemHeader(sheet: Sheet): Int? =
        (0..sheet.lastRowNum).firstOrNull { rowIndex ->
            sheet.cellText(rowIndex, 0).equals("ITEM #", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 1).equals("STYLE", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 6).equals("QTY", ignoreCase = true) &&
                    sheet.cellText(rowIndex, 7).contains("UNIT PRICE", ignoreCase = true)
        }

    private fun parseStreet(raw: String): StreetParts {
        val parts = raw
            .replace(Regex("""\s+"""), " ")
            .split(",")
            .map(String::trim)
            .filter(String::isNotBlank)

        return StreetParts(
            addressLine1 = parts.firstOrNull(),
            addressLine2 = parts.drop(1)
                .joinToString(", ")
                .takeIf(String::isNotBlank)
                ?.let { "$it, China" }
        )
    }

    private fun parseChinaCityLine(raw: String): CityParts {
        val cleaned = raw.replace(Regex("""\s+"""), " ").trim()
        val match = CITY_LINE_PATTERN.matchEntire(cleaned)
            ?: return CityParts(null, null, null)

        return CityParts(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim(),
            zip = match.groupValues[3]
        )
    }

    private fun normalizeSku(raw: String): String? {
        val normalized = raw.uppercase().replace(Regex("""\s+"""), "").trim()
        return normalized.takeIf(String::isNotBlank)
    }

    private fun Sheet.cellText(rowIndex: Int, columnIndex: Int): String =
        getRow(rowIndex)?.text(columnIndex).orEmpty()

    private fun XSSFWorkbook.findPurchaseOrderSheet(): Sheet? =
        (0 until numberOfSheets)
            .map(::getSheetAt)
            .firstOrNull { sheet -> sheet.sheetName.trim().equals(SHEET_NAME, ignoreCase = true) }

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

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class StreetParts(
        val addressLine1: String?,
        val addressLine2: String?
    )

    private data class CityParts(
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private companion object {
        const val SHEET_NAME = "Purchase Order"
        const val CUSTOMER_ID = "ZHANG JIAGANG SUREJI"

        val CITY_LINE_PATTERN = Regex(
            """^(.+?),\s*(.+?)\s+CHINA,?\s*(\d{6})$""",
            RegexOption.IGNORE_CASE
        )
    }
}
