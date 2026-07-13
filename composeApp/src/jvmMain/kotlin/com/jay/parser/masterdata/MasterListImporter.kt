package com.jay.parser.masterdata

import com.jay.parser.models.ItemCatalog
import com.jay.parser.models.MasterCustomer
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal
import java.math.RoundingMode

class MasterListImporter {

    data class ParsedMasterList(
        val bundle: MasterDataBundle,
        val warnings: List<String>
    )

    fun parse(file: File): ParsedMasterList {
        require(file.extension.equals("xlsx", ignoreCase = true)) {
            "Master list must be an .xlsx file."
        }

        FileInputStream(file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val customersSheet = workbook.getSheet(CUSTOMERS_SHEET)
                    ?: error("Missing required sheet: $CUSTOMERS_SHEET")
                val itemsSheet = workbook.getSheet(ITEMS_SHEET)
                    ?: error("Missing required sheet: $ITEMS_SHEET")
                val discountsSheet = workbook.getSheet(DISCOUNTS_SHEET)
                    ?: error("Missing required sheet: $DISCOUNTS_SHEET")

                val warnings = mutableListOf<String>()
                val customers = parseCustomers(customersSheet, warnings)
                val itemData = parseItems(itemsSheet, warnings)
                val qtyDiscounts = parseQtyDiscounts(discountsSheet, warnings)

                if (customers.isEmpty()) error("Customer sheet did not contain any customers.")
                if (itemData.descriptions.isEmpty()) error("Item sheet did not contain any item descriptions.")
                if (itemData.prices.isEmpty()) error("Item sheet did not contain any priced items.")

                return ParsedMasterList(
                    bundle = MasterDataBundle(
                        itemCatalog = ItemCatalog(
                            prices = itemData.prices,
                            descriptions = itemData.descriptions
                        ),
                        customers = customers,
                        glAccounts = itemData.glAccounts,
                        qtyDiscountRules = qtyDiscounts
                    ),
                    warnings = warnings
                )
            }
        }
    }

    private fun parseCustomers(
        sheet: Sheet,
        warnings: MutableList<String>
    ): List<MasterCustomer> {
        val headers = sheet.headerMap()
        requireHeaders(
            sheetName = sheet.sheetName,
            headers = headers,
            required = listOf("Customer ID", "Customer", "Terms", "Ship Via", "Price Level")
        )

        val seen = mutableSetOf<String>()
        val customers = mutableListOf<MasterCustomer>()

        for (row in sheet.rowsAfterHeader()) {
            val id = row.stringAt(headers["Customer ID"]).normalizedText()
            if (id.isBlank()) continue

            if (!seen.add(id.uppercase())) {
                warnings += "Duplicate customer ID skipped: $id"
                continue
            }

            customers += MasterCustomer(
                id = id,
                name = row.stringAt(headers["Customer"]).normalizedText(),
                terms = row.stringAt(headers["Terms"]).normalizedText(),
                shipVia = row.stringAt(headers["Ship Via"]).normalizedText(),
                priceLevel = row.stringAt(headers["Price Level"]).normalizedText()
            )
        }

        return customers
    }

    private fun parseItems(
        sheet: Sheet,
        warnings: MutableList<String>
    ): ParsedItemData {
        val headers = sheet.headerMap()
        requireHeaders(
            sheetName = sheet.sheetName,
            headers = headers,
            required = listOf("Item ID", "Item Description", "Sales Acct")
        )

        val priceColumns = headers
            .filterKeys { key -> key in KNOWN_PRICE_LEVELS }
            .toList()
            .sortedBy { it.second }

        if (priceColumns.isEmpty()) {
            error("Item sheet did not contain any known price level columns.")
        }

        val descriptions = linkedMapOf<String, String>()
        val prices = linkedMapOf<String, Map<String, Double>>()
        val glAccounts = linkedMapOf<String, String>()

        for (row in sheet.rowsAfterHeader()) {
            val sku = row.stringAt(headers["Item ID"]).normalizedSku()
            if (sku.isBlank()) continue

            val description = row.stringAt(headers["Item Description"]).normalizedText()
            if (description.isNotBlank()) {
                descriptions[sku] = description
            }

            val salesAcct = row.stringAt(headers["Sales Acct"]).normalizedAccount()
            if (salesAcct.isNotBlank()) {
                glAccounts[sku] = salesAcct
            }

            val rowPrices = linkedMapOf<String, Double>()
            for ((priceLevel, columnIndex) in priceColumns) {
                val price = row.numericAt(columnIndex)
                if (price != null) {
                    rowPrices[priceLevel] = price.roundPrice()
                }
            }

            if (rowPrices.isNotEmpty()) {
                prices[sku] = rowPrices
            }
        }

        val descriptionsWithoutGl = descriptions.keys.count { it !in glAccounts }
        if (descriptionsWithoutGl > 0) {
            warnings += "$descriptionsWithoutGl item description(s) have no Sales Acct."
        }

        return ParsedItemData(
            descriptions = descriptions,
            prices = prices,
            glAccounts = glAccounts
        )
    }

    private fun parseQtyDiscounts(
        sheet: Sheet,
        warnings: MutableList<String>
    ): List<MasterQtyDiscountRule> {
        val headers = sheet.headerValues()
        val normalizedHeaders = headers.map { normalizeHeader(it) }
        val customerIndex = normalizedHeaders.indexOf("Customer ID")
        val itemIndex = normalizedHeaders.indexOf("Item ID")
        val qtyDiscountIndex = normalizedHeaders.indexOf("Qty Discount ID")
        val priceLevelIndex = normalizedHeaders.indexOf("Price Level")

        if (customerIndex < 0 || itemIndex < 0 || qtyDiscountIndex < 0) {
            error("Qty Discounts sheet must contain Customer ID, Item ID, and Qty Discount ID columns.")
        }

        val breakColumnPairs = headers.indices
            .filter { normalizedHeaders[it] == "Min Qty for Discount" }
            .mapNotNull { minQtyIndex ->
                val discountIndex = (minQtyIndex + 1).takeIf {
                    it < normalizedHeaders.size && normalizedHeaders[it] == "Discount Percent"
                }
                discountIndex?.let { minQtyIndex to it }
            }

        if (breakColumnPairs.isEmpty()) {
            error("Qty Discounts sheet did not contain any discount break columns.")
        }

        val rules = mutableListOf<MasterQtyDiscountRule>()

        for (row in sheet.rowsAfterHeader()) {
            val customerId = row.stringAt(customerIndex).normalizedText()
            val itemId = row.stringAt(itemIndex).normalizedSku()
            val qtyDiscountId = row.stringAt(qtyDiscountIndex).normalizedSku()
            if (customerId.isBlank() && itemId.isBlank() && qtyDiscountId.isBlank()) continue

            if (customerId.isBlank() || itemId.isBlank()) {
                warnings += "Skipped incomplete quantity discount row ${row.rowNum + 1}."
                continue
            }

            val breaks = breakColumnPairs.mapNotNull { (minQtyIndex, discountIndex) ->
                val minQty = row.numericAt(minQtyIndex)
                val discount = row.numericAt(discountIndex)

                if (minQty != null && discount != null) {
                    MasterQtyDiscountBreak(
                        minQty = minQty,
                        discountPercent = discount
                    )
                } else {
                    null
                }
            }.sortedBy { it.minQty }

            if (breaks.isEmpty()) {
                warnings += "Skipped quantity discount row ${row.rowNum + 1} because it has no breaks."
                continue
            }

            rules += MasterQtyDiscountRule(
                customerId = customerId,
                itemId = itemId,
                qtyDiscountId = qtyDiscountId.ifBlank { itemId },
                priceLevel = row.stringAt(priceLevelIndex).normalizedText().ifBlank { null },
                breaks = breaks
            )
        }

        return rules
    }

    private fun Sheet.headerMap(): Map<String, Int> {
        return headerValues()
            .mapIndexedNotNull { index, value ->
                val header = normalizeHeader(value)
                if (header.isBlank()) null else header to index
            }
            .toMap()
    }

    private fun Sheet.headerValues(): List<String> {
        val header = getRow(0) ?: error("Missing header row in sheet: $sheetName")
        val lastCell = header.lastCellNum.toInt().coerceAtLeast(0)
        return (0 until lastCell).map { header.stringAt(it) }
    }

    private fun Sheet.rowsAfterHeader(): Sequence<Row> {
        return (1..lastRowNum)
            .asSequence()
            .mapNotNull { getRow(it) }
    }

    private fun requireHeaders(
        sheetName: String,
        headers: Map<String, Int>,
        required: List<String>
    ) {
        val missing = required.filter { normalizeHeader(it) !in headers }
        if (missing.isNotEmpty()) {
            error("Sheet $sheetName is missing required column(s): ${missing.joinToString(", ")}")
        }
    }

    private fun Row.stringAt(index: Int?): String {
        if (index == null || index < 0) return ""
        val cell = getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return cell.asString()
    }

    private fun Row.numericAt(index: Int?): Double? {
        if (index == null || index < 0) return null
        val cell = getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return null
        return cell.asDouble()
    }

    private fun Cell.asString(): String {
        return when (cellType) {
            CellType.STRING -> stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(this)) {
                    localDateTimeCellValue.toLocalDate().toString()
                } else {
                    val n = numericCellValue
                    if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
                }
            }
            CellType.BOOLEAN -> booleanCellValue.toString()
            CellType.FORMULA -> runCatching { numericCellValue }
                .map { n -> if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() }
                .getOrElse { toString() }
            else -> ""
        }
    }

    private fun Cell.asDouble(): Double? {
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue
            CellType.STRING -> stringCellValue.trim().replace(",", "").toDoubleOrNull()
            CellType.FORMULA -> runCatching { numericCellValue }.getOrNull()
            else -> null
        }
    }

    private fun Double.roundPrice(): Double {
        return BigDecimal.valueOf(this)
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun String.normalizedText(): String {
        return replace(Regex("""\s+"""), " ").trim()
    }

    private fun String.normalizedSku(): String {
        return normalizedText().uppercase()
    }

    private fun String.normalizedAccount(): String {
        val cleaned = normalizedText()
        return if (cleaned.endsWith(".0")) cleaned.dropLast(2) else cleaned
    }

    private fun normalizeHeader(value: String): String {
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    private data class ParsedItemData(
        val descriptions: Map<String, String>,
        val prices: Map<String, Map<String, Double>>,
        val glAccounts: Map<String, String>
    )

    private companion object {
        const val CUSTOMERS_SHEET = "Customer Master File List"
        const val ITEMS_SHEET = "Item Master List"
        const val DISCOUNTS_SHEET = "Qty Discounts"

        val KNOWN_PRICE_LEVELS = setOf(
            "DISTRIBUTOR",
            "DIST + 100%",
            "DIST + 75%",
            "DIST + 50%",
            "DIST + 25%",
            "DIST + 10%",
            "DIST - 5%",
            "DIST - 10%",
            "DIST - 15%",
            "P. EUROPE"
        )
    }
}
