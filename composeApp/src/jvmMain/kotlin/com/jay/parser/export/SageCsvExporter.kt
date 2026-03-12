package com.jay.parser.export

import com.jay.parser.models.ExportOrder
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SageCsvExporter {

    private val header = listOf(
        "Date",
        "Customer ID",
        "Customer Name",
        "Sales Order/Proposal #",
        "Customer PO",
        "Ship to Name",
        "Ship to Address-Line One",
        "Ship to Address-Line Two",
        "Ship to City",
        "Ship to State",
        "Ship to Zipcode",
        "Item ID",
        "Description",
        "Quantity",
        "Unit Price",
        "Amount",
        "Accounts Receivable account",
        "G/L Account",
        "Number of Distributions",
        "SO/Proposal Distribution",
        "Tax Type",
        "Ship Via",
        "Displayed Terms"
    )

    fun export(orders: List<ExportOrder>, outputFile: File, orderDate: LocalDate = LocalDate.now()) {
        val csv = buildCsv(orders, orderDate)
        val bytes = encodeWindows1252(csv)
        outputFile.writeBytes(bytes)
    }

    fun buildCsv(orders: List<ExportOrder>, orderDate: LocalDate = LocalDate.now()): String {
        val rows = mutableListOf<List<String>>()
        rows += header

        for (order in orders) {
            val distributionCount = order.lines.size
            val dateString = orderDate.format(DateTimeFormatter.ofPattern("M/d/yyyy"))

            order.lines.forEachIndexed { index, line ->
                val unitPrice = negativeMoney(line.unitPriceResolved)
                val amount = negativeMoney(line.unitPriceResolved * line.quantityForExport)

                rows += listOf(
                    dateString,
                    order.customer?.id.orEmpty(),
                    order.customer?.name.orEmpty(),
                    order.orderNumber,
                    order.orderNumber,
                    order.shipToCustomer.orEmpty(),
                    order.addressLine1.orEmpty(),
                    order.addressLine2.orEmpty(),
                    order.city.orEmpty(),
                    order.state.orEmpty(),
                    order.zip.orEmpty(),
                    line.sku,
                    line.description,
                    formatQuantity(line.quantityForExport),
                    formatMoney(unitPrice),
                    formatMoney(amount),
                    "1135",
                    line.glAccount,
                    distributionCount.toString(),
                    (index + 1).toString(),
                    "1",
                    order.customer?.shipVia.orEmpty(),
                    order.termsResolved.orEmpty()
                )
            }
        }

        return rows.joinToString("\r\n") { row ->
            row.joinToString(",") { cell -> quote(cell) }
        } + "\r\n"
    }

    private fun quote(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun formatQuantity(value: Double): String {
        val bd = BigDecimal.valueOf(value).stripTrailingZeros()
        return bd.toPlainString()
    }

    private fun formatMoney(value: Double): String {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
    }

    private fun negativeMoney(value: Double): Double {
        return if (value > 0) -value else value
    }

    /**
     * Strict Windows-1252 encoder for Sage compatibility.
     * Output is also built with CRLF line endings.
     */
    private fun encodeWindows1252(text: String): ByteArray {
        val bytes = ByteArray(text.length)

        for (i in text.indices) {
            val charCode = text[i].code

            bytes[i] = when {
                charCode < 128 -> charCode.toByte()
                charCode == 0x20AC -> 0x80.toByte()
                charCode == 0x201A -> 0x82.toByte()
                charCode == 0x0192 -> 0x83.toByte()
                charCode == 0x201E -> 0x84.toByte()
                charCode == 0x2026 -> 0x85.toByte()
                charCode == 0x2020 -> 0x86.toByte()
                charCode == 0x2021 -> 0x87.toByte()
                charCode == 0x02C6 -> 0x88.toByte()
                charCode == 0x2030 -> 0x89.toByte()
                charCode == 0x0160 -> 0x8A.toByte()
                charCode == 0x2039 -> 0x8B.toByte()
                charCode == 0x0152 -> 0x8C.toByte()
                charCode == 0x017D -> 0x8E.toByte()
                charCode == 0x2018 -> 0x91.toByte()
                charCode == 0x2019 -> 0x92.toByte()
                charCode == 0x201C -> 0x93.toByte()
                charCode == 0x201D -> 0x94.toByte()
                charCode == 0x2022 -> 0x95.toByte()
                charCode == 0x2013 -> 0x96.toByte()
                charCode == 0x2014 -> 0x97.toByte()
                charCode == 0x02DC -> 0x98.toByte()
                charCode == 0x2122 -> 0x99.toByte()
                charCode == 0x0161 -> 0x9A.toByte()
                charCode == 0x203A -> 0x9B.toByte()
                charCode == 0x0153 -> 0x9C.toByte()
                charCode == 0x017E -> 0x9E.toByte()
                charCode == 0x0178 -> 0x9F.toByte()
                charCode in 0xA0..0xFF -> charCode.toByte()
                else -> '?'.code.toByte()
            }
        }

        return bytes
    }
}