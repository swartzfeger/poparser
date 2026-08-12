package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoChlorSystemTnLayoutStrategyTest {

    @Test
    fun parsesPo46836AndUsesTheVialCountRatherThanTheStripCountAsUomDivisor() {
        val parsed = AutoChlorSystemTnLayoutStrategy().parse(
            listOf(
                "PURCHASE ORDER",
                "Auto-Chlor System, LLC",
                "46836 8/6/2026",
                "AUTO-CHLOR SYSTEM MEMPHIS PLANT (200)",
                "746 POPLAR AVE",
                "MEMPHIS TN 38105",
                "1% 10 NET 30",
                "ItemNumber Description Quantity UnitCost Ext.Cost",
                "R004 TESTPAPERpH0-4VIAL 300.00 VIAL 2.450000 735.00",
                "VendorItemNo: YOUR#PH0007-3-1V-25",
                "ReleaseQty DeliveryDate DeliveryDate Comment",
                "300.00 8/24/2026 8/24/2026",
                "R043 TESTPAPERHIGHLEVELQAC25/VIAL 3,000.00 VIAL 2.100000 6,300.00",
                "VendorItemNo: QAC-1500-1V-25(0-1500PPM",
                "ReleaseQty DeliveryDate DeliveryDate Comment",
                "3,000.00 8/24/2026 8/24/2026",
                "GrandTotal 7,035.00"
            )
        )

        assertEquals("46836", parsed.orderNumber)
        assertEquals(listOf("PH0007-3-1V-25", "QAC-1500-1V-25"), parsed.items.map { it.sku })
        assertEquals(listOf(300.0, 3000.0), parsed.items.map { it.quantity })
        assertEquals(listOf(2.45, 2.1), parsed.items.map { it.unitPrice })

        val enriched = OrderEnricher().enrich("46836PO.pdf", parsed)
        assertEquals(listOf(300.0, 3000.0), enriched.lines.map { it.quantityForExport })
        assertEquals(2.45, enriched.lines[0].unitPriceResolved, absoluteTolerance = 0.0001)
        assertEquals(2.1, enriched.lines[1].unitPriceResolved, absoluteTolerance = 0.0001)
        assertEquals(
            735.0,
            enriched.lines[0].quantityForExport * enriched.lines[0].unitPriceResolved,
            absoluteTolerance = 0.0001
        )
        assertEquals(
            6300.0,
            enriched.lines[1].quantityForExport * enriched.lines[1].unitPriceResolved,
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun parsesMalformedVendorItemRowsUsingReleaseQuantity() {
        val parsed = AutoChlorSystemTnLayoutStrategy().parse(
            listOf(
                "Auto-Chlor System, LLC",
                "46674 7/13/2026",
                "AUTO-CHLOR SYSTEM MEMPHIS PLANT (200)",
                "ItemNumber Description Quantity UnitCost Ext.Cost",
                "R006 HIGHLEVELQACQR0-400PPMPLASTIC 120.00 VIAL 1.950000 234.00",
                "VendorItemNo: QAC-400-1V-25",
                "Requested Promised",
                "ReleaseQty DeliveryDate DeliveryDate Comment",
                "120.00 8/17/2026 8/17/2026",
                "R044 TESTPAPERPAASANITIZER0-160PPM 25/VIALOXY2M0I0Z.0E0LVOIWALLEVEL 1.950000 390.00",
                "VendorItemNo: PAA-160-1V-25",
                "PERACETICACID1-160PPMPLASTICSTRIPS1VIALMICROVIAL25STRIPS/VIAL",
                "Requested Promised",
                "ReleaseQty DeliveryDate DeliveryDate Comment",
                "200.00 8/17/2026 8/17/2026",
                "GrandTotal 624.00"
            )
        )

        assertEquals(2, parsed.items.size)
        assertEquals("QAC-400-1V-25", parsed.items[0].sku)
        assertEquals(120.0, parsed.items[0].quantity)
        assertEquals(1.95, parsed.items[0].unitPrice)
        assertEquals("PAA-160-1V-25", parsed.items[1].sku)
        assertEquals(200.0, parsed.items[1].quantity)
        assertEquals(1.95, parsed.items[1].unitPrice)
    }
}
