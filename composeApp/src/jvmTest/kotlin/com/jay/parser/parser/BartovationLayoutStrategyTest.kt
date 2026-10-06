package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class BartovationLayoutStrategyTest {

    @Test
    fun appliesFixedQac1500PriceForFiveHundredUnitOrder() {
        val parsed = BartovationLayoutStrategy().parse(
            listOf(
                "BARTOVATION LLC P.O. # PL10626",
                "BARTOVATION LLC",
                "INVENTORY RECEIVING",
                "2485 47TH ST",
                "ASTORIA, NY 11103",
                "UPS Ground - Bill Receiver UPS Account # 935E5X",
                "PART # TITLE QTY UNIT PRICE TOTAL",
                "QAC-1500-1V-50 QAC 1500 Test Strips Vial of 50 500 $3.75 $1,875.00"
            )
        )

        val line = OrderEnricher().enrich("Bartovation PO PL10626.pdf", parsed).lines.single()

        assertEquals("QAC-1500-1V-50", line.sku)
        assertEquals(500.0, line.quantityForExport)
        assertEquals(3.75, line.unitPriceResolved)
    }

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
