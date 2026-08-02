package com.jay.parser.export

import com.jay.parser.models.ExportOrder
import com.jay.parser.models.ExportOrderLine
import com.jay.parser.models.PackagingSummary
import com.jay.parser.models.ResolvedCustomer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class SageCsvExporterTest {

    @Test
    fun exportsUnitPricesWithThreeDecimals() {
        val csv = SageCsvExporter().buildCsv(
            orders = listOf(
                ExportOrder(
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
                        status = "Complete"
                    )
                )
            ),
            orderDate = LocalDate.of(2026, 7, 13)
        )

        assertTrue(csv.contains("\"-464.188\""))
        assertTrue(csv.contains("\"-464.19\""))
        assertTrue(csv.contains("\"Item Unit Volume (cu in)\""))
        assertTrue(csv.contains("\"275\",\"2.5\",\"305.556\",\"2.5\",\"1\",\"1x Box 12 (12 x 9 x 4)\",\"Complete\""))
    }
}
