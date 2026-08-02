package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class BartovationLayoutStrategyTest {

    @Test
    fun keepsPlblPriceFromMasterData() {
        val parsed = BartovationLayoutStrategy().parse(
            listOf(
                "BARTOVATIONLLC P.O.# PL071326",
                "BARTOVATIONLLC",
                "INVENTORYRECEIVING",
                "248547THST",
                "ASTORIA,NY11103",
                "UPSGround-BillReceiver UPSAccount#935E5X",
                "PART# TITLE QTY UNITPRICE TOTAL",
                "PLBL UNBRANDED 200 $0.15 $30.00"
            )
        )

        val enriched = OrderEnricher().enrich("Bartovation PO PL071326.pdf", parsed)
        val line = enriched.lines.single()

        assertEquals("PLBL", line.sku)
        assertEquals("PAPER LABELS", line.description)
        assertEquals(200.0, line.quantityForExport)
        assertEquals(0.15, line.unitPriceResolved)
        assertEquals("4210", line.glAccount)
    }
}
