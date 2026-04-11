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

class FlowChemExcelParser {

    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { wb ->
                    val sheet = wb.getSheet("Hoja1") ?: return false
                    val b1 = sheet.getCellString(0, 1)
                    val b4 = sheet.getCellString(3, 1)
                    val b17 = sheet.getCellString(16, 1)

                    b1.contains("PO NUMBER", ignoreCase = true) &&
                            b4.contains("FLOW CHEM", ignoreCase = true) &&
                            b17.equals("DESCRIPTION", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun parse(file: File): ParsedPdfFields {
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { wb ->
                val sheet = wb.getSheet("Hoja1")
                    ?: error("Missing Hoja1 sheet in ${file.name}")

                val customerName = "FLOW CHEM S.A.S."
                val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

                val orderNumber = parseOrderNumber(sheet.getCellString(0, 1))
                val cityCountry = sheet.getCellString(6, 1)

                val address = parseCityCountry(cityCountry)

                val items = mutableListOf<ParsedPdfItem>()

                var rowIndex = 17 // Excel row 18, zero-based
                while (true) {
                    val row = sheet.getRow(rowIndex) ?: break

                    val descriptionCell = row.getStringOrBlank(1)
                    val qty = row.getNumericOrNull(2)
                    val unitPrice = row.getNumericOrNull(3)

                    if (descriptionCell.isBlank()) {
                        val marker = row.getStringOrBlank(3)
                        if (marker.equals("SUBTOTAL", ignoreCase = true)) break
                        rowIndex++
                        continue
                    }

                    if (descriptionCell.equals("DESCRIPTION", ignoreCase = true)) {
                        rowIndex++
                        continue
                    }

                    if (qty == null || unitPrice == null) {
                        rowIndex++
                        continue
                    }

                    val (sku, description) = splitSkuAndDescription(descriptionCell)

                    items.add(
                        ParsedPdfItem(
                            sku = sku,
                            description = description,
                            quantity = qty,
                            unitPrice = unitPrice
                        )
                    )

                    rowIndex++
                }

                return ParsedPdfFields(
                    customerName = customerName,
                    orderNumber = orderNumber,
                    shipToCustomer = customerName,
                    addressLine1 = sheet.getCellString(5, 1).removePrefix("ADDRESS:").trim().ifBlank { null },
                    addressLine2 = null,
                    city = address.city,
                    state = address.state,
                    zip = address.zip,
                    terms = mappedCustomer?.terms,
                    items = items
                )
            }
        }
    }

    private fun parseOrderNumber(raw: String): String? {
        return Regex("""PO\s*NUMBER\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues?.get(1)
            ?.trim()
    }

    private fun splitSkuAndDescription(raw: String): Pair<String?, String> {
        val cleaned = raw.replace(Regex("""\s+"""), " ").trim()

        val parsedSku = Regex("""^([A-Z0-9-]+)\b""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues?.get(1)
            ?.trim()
            ?.uppercase()

        val description = if (parsedSku != null) {
            cleaned.removePrefix(parsedSku).trim().ifBlank { cleaned }
        } else {
            cleaned
        }

        val normalizedSku = normalizeFlowChemSku(
            sku = parsedSku,
            description = description
        )

        return normalizedSku to description
    }

    private fun normalizeFlowChemSku(sku: String?, description: String): String? {
        if (sku.isNullOrBlank()) return sku

        val normalizedDescription = description.uppercase()
        val normalizedSku = sku.uppercase().trim()

        return when (normalizedSku) {
            "FLO-QA-15-100" -> "FLO-QA15-100"
            "FLO-CH-05-50" -> "FLO-CH05-50"
            "PAA1000" -> {
                if (normalizedDescription.contains("50STRIPS")) {
                    "PAA-1000-1V-50"
                } else {
                    "PAA1000"
                }
            }
            else -> normalizedSku
        }
    }

    private fun parseCityCountry(raw: String): AddressParts {
        val cleaned = raw.replace(Regex("""\s+"""), " ").trim()
        val parts = cleaned.split("/").map { it.trim() }

        return when {
            parts.size >= 2 -> AddressParts(
                city = parts[0].ifBlank { null },
                state = parts[1].ifBlank { null },
                zip = null
            )
            else -> AddressParts(
                city = cleaned.ifBlank { null },
                state = null,
                zip = null
            )
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

    private data class AddressParts(
        val city: String?,
        val state: String?,
        val zip: String?
    )
}