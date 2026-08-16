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

class GeneralRubberPlasticsExcelParser {
    fun canParse(file: File): Boolean {
        if (!file.extension.equals("xlsx", ignoreCase = true)) return false

        return try {
            FileInputStream(file).use { input ->
                XSSFWorkbook(input).use { workbook ->
                    (0 until workbook.numberOfSheets)
                        .map(workbook::getSheetAt)
                        .any { sheet ->
                            sheet.containsEmailDomain() && findItemColumns(sheet) != null
                        }
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
                    .firstOrNull { candidate ->
                        candidate.containsEmailDomain() && findItemColumns(candidate) != null
                    }
                    ?: error("Missing General Rubber & Plastics purchase-order sheet in ${file.name}")
                val columns = findItemColumns(sheet)
                    ?: error("Missing General Rubber & Plastics item header in ${file.name}")
                val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

                val items = buildList {
                    for (rowIndex in (columns.headerRow + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val rawQuantity = row.numericOrNull(columns.quantity) ?: continue
                        if (rawQuantity <= 0.0) continue

                        val rawSku = normalizeSku(row.text(columns.vendorItem)) ?: continue
                        val rawUnitPrice = row.numericOrNull(columns.cost)
                        val line = normalizeSellableLine(
                            rawSku = rawSku,
                            rawQuantity = rawQuantity,
                            rawUnitPrice = rawUnitPrice,
                            customerPriceLevel = customer?.priceLevel
                        )

                        add(
                            ParsedPdfItem(
                                sku = line.sku,
                                description = ItemMapper.getItemDescription(line.sku).ifBlank { line.sku },
                                quantity = line.quantity,
                                unitPrice = line.unitPrice,
                                uom = columns.uom?.let { columnIndex -> row.text(columnIndex) }?.ifBlank { null }
                            )
                        )
                    }
                }

                return ParsedPdfFields(
                    customerName = CUSTOMER_ID,
                    orderNumber = findOrderNumber(sheet),
                    terms = customer?.terms,
                    items = items
                )
            }
        }
    }

    private fun findOrderNumber(sheet: Sheet): String? {
        for (rowIndex in 0..minOf(sheet.lastRowNum, 20)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val lastColumn = row.lastCellNum.coerceAtLeast(0).toInt()
            for (columnIndex in 0 until lastColumn) {
                if (!compact(row.text(columnIndex)).contains("PURCHASEORDER")) continue

                for (candidateColumn in (columnIndex + 1) until lastColumn) {
                    val candidate = row.text(candidateColumn).trim()
                    if (ORDER_NUMBER_PATTERN.matches(candidate)) return candidate.uppercase()
                }
            }
        }
        return null
    }

    private fun findItemColumns(sheet: Sheet): ItemColumns? {
        for (rowIndex in 0..minOf(sheet.lastRowNum, 30)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val headings = (0 until row.lastCellNum.coerceAtLeast(0).toInt())
                .associateWith { columnIndex -> compact(row.text(columnIndex)) }

            val quantity = headings.entries.firstOrNull { it.value == "QTY" }?.key ?: continue
            val vendorItem = headings.entries
                .firstOrNull { it.value.contains("VENDORITEMNUMBER") }
                ?.key ?: continue
            val cost = headings.entries.firstOrNull { it.value == "COST" }?.key ?: continue
            val uom = headings.entries.firstOrNull { it.value in setOf("UOM", "UNITOFMEASURE") }?.key

            return ItemColumns(rowIndex, quantity, uom, vendorItem, cost)
        }
        return null
    }

    private fun Sheet.containsEmailDomain(): Boolean =
        (0..lastRowNum).any { rowIndex ->
            val row = getRow(rowIndex) ?: return@any false
            (0 until row.lastCellNum.coerceAtLeast(0).toInt())
                .any { columnIndex -> row.text(columnIndex).contains(EMAIL_DOMAIN, ignoreCase = true) }
        }

    private fun normalizeSku(raw: String): String? = raw
        .uppercase()
        .replace(Regex("""\s+"""), "")
        .trim()
        .takeIf(String::isNotBlank)

    private fun normalizeSellableLine(
        rawSku: String,
        rawQuantity: Double,
        rawUnitPrice: Double?,
        customerPriceLevel: String?
    ): NormalizedLine {
        if (rawSku != SOURCE_TWELVE_BAG_SKU) {
            return NormalizedLine(rawSku, rawQuantity, rawUnitPrice)
        }

        val masterSingleBagPrice = ItemMapper.getItemPrice(
            sku = SELLABLE_SINGLE_BAG_SKU,
            priceLevel = customerPriceLevel.orEmpty()
        )
        val sourceUsesTwelveBagPackPrice = rawUnitPrice != null &&
                masterSingleBagPrice > 0.0 &&
                rawUnitPrice > masterSingleBagPrice * 2.0

        return if (sourceUsesTwelveBagPackPrice) {
            NormalizedLine(
                sku = SELLABLE_SINGLE_BAG_SKU,
                quantity = rawQuantity * TWELVE_BAG_PACK_SIZE,
                unitPrice = rawUnitPrice / TWELVE_BAG_PACK_SIZE
            )
        } else {
            NormalizedLine(SELLABLE_SINGLE_BAG_SKU, rawQuantity, rawUnitPrice)
        }
    }

    private fun Row.text(columnIndex: Int): String {
        val cell = getCell(columnIndex) ?: return ""
        return DataFormatter().formatCellValue(cell).trim()
    }

    private fun Row.numericOrNull(columnIndex: Int): Double? {
        val cell = getCell(columnIndex) ?: return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue
                .trim()
                .removePrefix("$")
                .replace(",", "")
                .toDoubleOrNull()
            CellType.FORMULA -> runCatching { cell.numericCellValue }.getOrNull()
            else -> null
        }
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ItemColumns(
        val headerRow: Int,
        val quantity: Int,
        val uom: Int?,
        val vendorItem: Int,
        val cost: Int
    )

    private data class NormalizedLine(
        val sku: String,
        val quantity: Double,
        val unitPrice: Double?
    )

    private companion object {
        const val CUSTOMER_ID = "GENERAL RUBBER & PLA"
        const val EMAIL_DOMAIN = "@grpsey.com"
        const val SOURCE_TWELVE_BAG_SKU = "PH0114-12B-50"
        const val SELLABLE_SINGLE_BAG_SKU = "PH0114-1B-50"
        const val TWELVE_BAG_PACK_SIZE = 12.0
        val ORDER_NUMBER_PATTERN = Regex("""(?i)^SI[A-Z0-9-]+$""")
    }
}
