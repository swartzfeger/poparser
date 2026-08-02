package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoChlorSystemTnLayoutStrategyTest {

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
