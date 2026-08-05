package com.jay.parser.export

import com.jay.parser.models.ExportOrder
import com.jay.parser.models.ExportOrderLine
import com.jay.parser.models.PackagingSummary
import com.jay.parser.models.ResolvedCustomer
import org.apache.commons.csv.CSVFormat
import java.io.StringReader
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SageCsvExporterTest {

    @Test
    fun exportsUnitPricesWithThreeDecimals() {
        val csv = SageCsvExporter().buildCsv(
            orders = listOf(testOrder()),
            orderDate = LocalDate.of(2026, 7, 13)
        )

        assertTrue(csv.contains("\"-464.188\""))
        assertTrue(csv.contains("\"-464.19\""))
        assertTrue(csv.contains("\"Invoice Note\""))
        assertTrue(csv.contains("\"(1 @ 2.5 lbs, Box #12 (12 x 9 x 4) Contains Line 1)\""))
        assertFalse(csv.contains("\"Item Unit Volume (cu in)\""))
        assertFalse(csv.contains("\"Item Unit Weight (lb)\""))
        assertFalse(csv.contains("\"Order Packed Volume (cu in)\""))
        assertFalse(csv.contains("\"Order Weight (lb)\""))
        assertFalse(csv.contains("\"Total Boxes\""))
        assertFalse(csv.contains("\"Box Plan\""))
        assertFalse(csv.contains("\"Packaging Status\""))
    }

    @Test
    fun leavesInvoiceNoteBlankWhenDisabled() {
        val csv = SageCsvExporter().buildCsv(
            orders = listOf(testOrder()),
            orderDate = LocalDate.of(2026, 7, 13),
            noInvoiceNote = true
        )

        assertTrue(csv.contains("\"Net 30\",\"\""))
        assertFalse(csv.contains("Contains Line 1"))
    }

    @Test
    fun includesInvoiceNoteOnlyOnFirstOrderLine() {
        val order = testOrder()
        val secondLine = order.lines.single().copy(
            sku = "145-12V-100",
            description = "CHLORINE TEST PAPERS"
        )
        val csv = SageCsvExporter().buildCsv(
            orders = listOf(order.copy(lines = order.lines + secondLine)),
            orderDate = LocalDate.of(2026, 8, 5)
        )
        val records = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(StringReader(csv))
            .records

        assertEquals(2, records.size)
        assertEquals(order.packaging.invoiceNote, records[0].get("Invoice Note"))
        assertEquals("", records[1].get("Invoice Note"))
    }

    private fun testOrder(): ExportOrder = ExportOrder(
        sourceFilename = "test.pdf",
        customer = ResolvedCustomer(
            id = "TEST",
            name = "Test Customer",
            terms = "Net 30",
            shipVia = "UPS",
            priceLevel = "DISTRIBUTOR"
        ),
        orderNumber = "PO-1",
        customerNameRaw = "Test Customer",
        shipToCustomer = null,
        addressLine1 = null,
        addressLine2 = null,
        city = null,
        state = null,
        zip = null,
        termsRaw = null,
        termsResolved = "Net 30",
        lines = listOf(
            ExportOrderLine(
                sku = "106-144V-100",
                description = "QAC TEST PAPERS",
                quantityRaw = 1.0,
                quantityForExport = 1.0,
                unitPriceReference = 464.188,
                unitPriceResolved = 464.188,
                glAccount = "4020",
                itemDimensions = "10 x 10 x 2.75",
                itemUnitVolumeCubicInches = 275.0,
                itemUnitWeightPounds = 2.5
            )
        ),
        packaging = PackagingSummary(
            orderPackedVolumeCubicInches = 305.556,
            orderWeightPounds = 2.5,
            totalBoxes = 1,
            boxPlan = "1x Box 12 (12 x 9 x 4)",
            invoiceNote = "(1 @ 2.5 lbs, Box #12 (12 x 9 x 4) Contains Line 1)",
            status = "Complete"
        )
    )
}
