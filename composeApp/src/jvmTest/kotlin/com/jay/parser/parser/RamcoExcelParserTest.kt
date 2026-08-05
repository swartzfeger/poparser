package com.jay.parser.parser

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RamcoExcelParserTest {
    private val parser = RamcoExcelParser()

    @Test
    fun parsesRamcoWorkbookAndMapsDescriptionToMasterSku() {
        val file = createWorkbook(
            orderNumber = 349772.0,
            quantity = 5.0,
            description = "Universal Test Papers 1.5\" x 25\" (100 Pack)",
            unitPrice = 145.0
        )

        try {
            assertTrue(parser.canParse(file))
            val enriched = OrderEnricher().enrich(file.name, parser.parse(file))

            assertEquals("349772", enriched.orderNumber)
            assertEquals("RAMCO", enriched.customer?.id)
            assertEquals("RAMCO MANUFACTURING COMPANY INC.", enriched.shipToCustomer)
            assertEquals("5634 HOGUE ST", enriched.addressLine1)
            assertEquals("HOUSTON", enriched.city)
            assertEquals("TX", enriched.state)
            assertEquals("77087", enriched.zip)
            assertEquals("210-100-1525", enriched.lines.single().sku)
            assertEquals(5.0, enriched.lines.single().quantityForExport)
        } finally {
            file.delete()
        }
    }

    @Test
    fun mapsBulkUniversalPaperDescriptionToMasterSku() {
        val file = createWorkbook(
            orderNumber = 348968.0,
            quantity = 15.0,
            description = "Universal Test Papers 1.5 x 3 (1000 pkgd bulk)",
            unitPrice = 125.0
        )

        try {
            val parsed = parser.parse(file)

            assertEquals("210-1000-1.53", parsed.items.single().sku)
            assertEquals(15.0, parsed.items.single().quantity)
        } finally {
            file.delete()
        }
    }

    private fun createWorkbook(
        orderNumber: Double,
        quantity: Double,
        description: String,
        unitPrice: Double
    ): File {
        val file = File.createTempFile("ramco-order-", ".xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")
            sheet.createRow(7).createCell(3).setCellValue("Purchase Order Number")
            sheet.createRow(8).createCell(3).setCellValue(orderNumber)
            sheet.createRow(15).createCell(1).setCellValue("Ramco Manufacturing Company Inc.")
            sheet.createRow(26).apply {
                createCell(0).setCellValue("QTY")
                createCell(1).setCellValue("PART NUMBER / DESCRIPTION")
                createCell(4).setCellValue("NET EA")
            }
            sheet.createRow(27).apply {
                createCell(0).setCellValue(quantity)
                createCell(1).setCellValue(description)
                createCell(4).setCellValue(unitPrice)
            }
            file.outputStream().use(workbook::write)
        }
        return file
    }
}
