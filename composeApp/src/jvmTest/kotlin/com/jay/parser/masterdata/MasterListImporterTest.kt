package com.jay.parser.masterdata

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MasterListImporterTest {

    @Test
    fun parsesWorkbookIntoMasterDataBundle() {
        val file = createWorkbook()

        val parsed = MasterListImporter().parse(file)
        val bundle = parsed.bundle

        assertEquals(1, bundle.customers.size)
        assertEquals("TEST CUSTOMER", bundle.customers.first().id)
        assertEquals("UPS GRNC", bundle.customers.first().shipVia)

        assertEquals("TEST STRIPS", bundle.itemCatalog.descriptions["ABC-1V-50"])
        assertEquals("4020", bundle.glAccounts["ABC-1V-50"])
        assertEquals(12.345, bundle.itemCatalog.prices["ABC-1V-50"]?.get("DISTRIBUTOR"))
        assertEquals(18.518, bundle.itemCatalog.prices["ABC-1V-50"]?.get("DIST + 50%"))

        assertEquals(1, bundle.qtyDiscountRules.size)
        val discount = bundle.qtyDiscountRules.first()
        assertEquals("ALL CUSTOMERS", discount.customerId)
        assertEquals("ABC-1V-50", discount.itemId)
        assertEquals("ABC", discount.qtyDiscountId)
        assertEquals("DISTRIBUTOR", discount.priceLevel)
        assertEquals(listOf(50.0, 100.0), discount.breaks.map { it.minQty })
        assertEquals(listOf(0.03, 0.05), discount.breaks.map { it.discountPercent })
        assertTrue(parsed.warnings.isEmpty())
    }

    private fun createWorkbook(): File {
        val file = File.createTempFile("master-list-importer-test-", ".xlsx")
        file.deleteOnExit()

        XSSFWorkbook().use { workbook ->
            workbook.createSheet("Customer Master File List").apply {
                createRow(0).apply {
                    createCell(0).setCellValue("Customer ID")
                    createCell(1).setCellValue("Customer")
                    createCell(2).setCellValue("Terms")
                    createCell(3).setCellValue("Ship Via")
                    createCell(4).setCellValue("Price Level")
                }
                createRow(1).apply {
                    createCell(0).setCellValue("TEST CUSTOMER")
                    createCell(1).setCellValue("Test Customer Inc")
                    createCell(2).setCellValue("Net 30 Days")
                    createCell(3).setCellValue("UPS GRNC")
                    createCell(4).setCellValue("DISTRIBUTOR")
                }
            }

            workbook.createSheet("Item Master List").apply {
                createRow(0).apply {
                    createCell(0).setCellValue("Item ID")
                    createCell(1).setCellValue("Item Description")
                    createCell(2).setCellValue("Item Class")
                    createCell(3).setCellValue("Sales Acct")
                    createCell(4).setCellValue("DISTRIBUTOR   ")
                    createCell(5).setCellValue("DIST + 50%    ")
                    createCell(6).setCellValue("Qty Discount ID")
                }
                createRow(1).apply {
                    createCell(0).setCellValue("ABC-1V-50")
                    createCell(1).setCellValue("TEST STRIPS")
                    createCell(2).setCellValue("Substock item")
                    createCell(3).setCellValue("4020")
                    createCell(4).setCellValue(12.345)
                    createCell(5).setCellValue(18.518)
                    createCell(6).setCellValue("ABC")
                }
            }

            workbook.createSheet("Qty Discounts").apply {
                createRow(0).apply {
                    createCell(0).setCellValue("Customer ID")
                    createCell(1).setCellValue("Item ID")
                    createCell(2).setCellValue("Qty Discount ID")
                    createCell(3).setCellValue("Min Qty for Discount")
                    createCell(4).setCellValue("Discount Percent")
                    createCell(5).setCellValue("Min Qty for Discount")
                    createCell(6).setCellValue("Discount Percent")
                    createCell(7).setCellValue("Price Level")
                }
                createRow(1).apply {
                    createCell(0).setCellValue("ALL CUSTOMERS")
                    createCell(1).setCellValue("ABC-1V-50")
                    createCell(2).setCellValue("ABC")
                    createCell(3).setCellValue(50.0)
                    createCell(4).setCellValue(0.03)
                    createCell(5).setCellValue(100.0)
                    createCell(6).setCellValue(0.05)
                    createCell(7).setCellValue("DISTRIBUTOR")
                }
            }

            file.outputStream().use { workbook.write(it) }
        }

        return file
    }
}
