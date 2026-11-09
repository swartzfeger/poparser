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

class SummitSupplyExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    workbook.any { sheet -> isLegacySheet(sheet) || isSupplySourceSheet(sheet) }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.firstOrNull { isLegacySheet(it) || isSupplySourceSheet(it) }
                    ?: error("Missing Summit Supply worksheet in ${file.name}")
                if (isSupplySourceSheet(sheet)) return parseSupplySourceSheet(sheet)
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

    private fun isLegacySheet(sheet: Sheet): Boolean =
        sheet.cellText(0, 0).equals("PRECISION LABORATORIES", ignoreCase = true) &&
            sheet.cellText(6, 0).equals("PO:", ignoreCase = true) &&
            sheet.cellText(8, 1).equals(CUSTOMER_NAME, ignoreCase = true) &&
            findItemHeader(sheet) != null

    private fun isSupplySourceSheet(sheet: Sheet): Boolean {
        val header = findSupplySourceHeader(sheet) ?: return false
        val text = (0 until header).joinToString(" ") { row ->
            sheet.getRow(row)?.joinToString(" ") { DataFormatter().formatCellValue(it) }.orEmpty()
        }.uppercase().replace(Regex("""\s+"""), " ")

        // This template carries the customer name only in an embedded logo.
        // Its address plus the specific order/table labels identify the logo-only form.
        val summitIdentity = text.contains(CUSTOMER_NAME) || SUMMIT_ADDRESS_PATTERN.containsMatchIn(text)
        return summitIdentity && text.contains("PRECISION LAB") &&
            findPurchaseOrderRow(sheet, header) != null
    }

    private fun findSupplySourceHeader(sheet: Sheet): Int? =
        (0..sheet.lastRowNum).firstOrNull { row ->
            sheet.cellText(row, 0).equals("Supply Source Item #", ignoreCase = true) &&
                sheet.cellText(row, 1).equals("Product Description", ignoreCase = true) &&
                sheet.cellText(row, 2).equals("Price - each", ignoreCase = true) &&
                sheet.cellText(row, 3).equals("qty/case", ignoreCase = true) &&
                sheet.cellText(row, 4).equals("Extended Price", ignoreCase = true)
        }

    private fun findPurchaseOrderRow(sheet: Sheet, header: Int): Int? =
        (0 until header).firstOrNull { row ->
            sheet.cellText(row, 0).equals("Purchase Order #:", ignoreCase = true)
        }

    private fun parseSupplySourceSheet(sheet: Sheet): ParsedPdfFields {
        val header = findSupplySourceHeader(sheet) ?: error("Missing Summit Supply item header")
        val poRow = findPurchaseOrderRow(sheet, header) ?: error("Missing Summit Supply PO number")
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_NAME)
        val address = (0 until header).firstNotNullOfOrNull { row ->
            SINGLE_LINE_ADDRESS_PATTERN.matchEntire(sheet.cellText(row, 0))
        }
        val items = buildList {
            for (rowIndex in (header + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                if (row.text(3).equals("Total:", ignoreCase = true)) break
                val sku = normalizeSku(row.text(0)) ?: continue
                val quantity = row.numericOrNull(3)?.takeIf { it > 0 } ?: continue
                val unitPrice = row.numericOrNull(2) ?: continue
                add(ParsedPdfItem(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { row.text(1).ifBlank { sku } },
                    quantity = quantity,
                    unitPrice = unitPrice
                ))
            }
        }
        return ParsedPdfFields(
            customerName = CUSTOMER_NAME,
            orderNumber = sheet.cellText(poRow, 1).ifBlank { null },
            shipToCustomer = CUSTOMER_NAME,
            addressLine1 = address?.groupValues?.get(1)?.trim(),
            city = address?.groupValues?.get(2)?.trim(),
            state = address?.groupValues?.get(3)?.uppercase(),
            zip = address?.groupValues?.get(4),
            terms = customer?.terms,
            items = items
        )
    }

    private fun findItemHeader(sheet: Sheet): Int? =
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
        val SUMMIT_ADDRESS_PATTERN = Regex(
            """\b25\s+COMMERCIAL\s+DRIVE,?\s+UNIT\s+3A,?\s+BRENTWOOD,?\s+NH\s+03833\b"""
        )
        val SINGLE_LINE_ADDRESS_PATTERN = Regex(
            """^(.+),\s*([^,]+),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        )
    }
}
