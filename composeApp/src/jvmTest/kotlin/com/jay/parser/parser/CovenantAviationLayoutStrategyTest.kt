package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CovenantAviationLayoutStrategyTest {

    private val strategy = CovenantAviationLayoutStrategy()

    @Test
    fun generatesCovenantOrderNumberFromNoisyOcrDate() {
        val expectedByOcrLine = mapOf(
            "DATE: 3719/2026]" to "COV031926",
            "DATE: [-- 4/3/2026] Entered By: [Bill R]" to "COV040326",
            "DATE: [10/16/2025]" to "COV101625",
            "DATE: [1271172025]" to "COV121125",
            "DATE: | 171972026]" to "COV011926",
            "DATE: | 77772026]" to "COV070726",
            "DATE: | 87372026]" to "COV080326",
            "DATE: 9/10/2026" to "COV091026"
        )

        for ((dateLine, expected) in expectedByOcrLine) {
            val parsed = strategy.parse(baseLines(dateLine))
            assertEquals(expected, parsed.orderNumber, dateLine)
        }
    }

    @Test
    fun parsesNoisyScannedOrderAndUsesMasterData() {
        val lines = baseLines(
            "DATE: | 87372026] Entered By: [Bill R]",
            "VENDOR: Precision Laboratories, Inc. SHIP TO: MALLORY SAFETY & SUPPLY LLC",
            "928-649-9833 44380 OSGOOD ROAD",
            "orders@preclaboratories.com FREMONT, CA 94539",
            "ATTN: TJ TAFU",
            "PO# |CAS-S49650WJR | TERMS: Net 30",
            "PART NUMBER DESCRIPTION QTY | UOM] PRICE EXT-PRICE",
            "TSAPER100 TSA PLASTIC STRIPS, 1 VIAL (CSP), 50 STRIPS/VIAL 250] VL |S 6.00]$ 1,500.00",
            "TOTAL: | $ 1,500.00 |"
        )

        assertTrue(strategy.matches(lines))

        val parsed = strategy.parse(lines)
        assertEquals("Covenant Aviation Security, LLC", parsed.customerName)
        assertEquals("COV080326", parsed.orderNumber)
        assertEquals("Mallory Safety & Supply LLC", parsed.shipToCustomer)
        assertEquals("44380 Osgood Road", parsed.addressLine1)
        assertEquals("ATTN: TJ TAFU", parsed.addressLine2)
        assertEquals("Fremont", parsed.city)
        assertEquals("CA", parsed.state)
        assertEquals("94539", parsed.zip)

        val item = parsed.items.single()
        assertEquals("TSAPER100", item.sku)
        assertEquals(250.0, item.quantity)
        assertEquals(6.0, item.unitPrice)

        val line = OrderEnricher().enrich("Scan2026-08-03_104015.pdf", parsed).lines.single()
        assertEquals("TSAPER100", line.sku)
        assertEquals(250.0, line.quantityForExport)
        assertEquals(6.0, line.unitPriceResolved)
        assertEquals("4030", line.glAccount)
    }

    @Test
    fun reconstructsItemWhenOcrSplitsPartNumberFromPriceRow() {
        val parsed = strategy.parse(
            baseLines(
                "DATE: 4/3/2026",
                "DESCRIPTION | QTY | UOM | PRICE EXT-PRICE",
                "TSA PLASTIC STRIPS, 1 VIAL (CSP), 50 STRIPS/VIAL",
                "TOTAL: S 1,500.00",
                "PART NUMBER",
                "TSAPER100",
                "PLEASE SEND ORDER CONFIRMATION AND INVOICE TO:",
                "bill.rosenberger@mallory.com",
                "Zachary.Hollenbach@covenantsecurity.com",
                "chrissy.schultz@covenantsecurity.com",
                "QTY | UOM | PRICE EXT-PRICE",
                "250] VL |$ 6.00|$ 1,500.00"
            )
        )

        val item = parsed.items.single()
        assertEquals("TSAPER100", item.sku)
        assertEquals(250.0, item.quantity)
        assertEquals(6.0, item.unitPrice)
    }

    private fun baseLines(vararg body: String): List<String> {
        return listOf(
            "COVENANT AVIATION SECURITY, LLC",
            "1112 W BOUGHTON ROAD - #355",
            "BOLINGBROOK, IL 60440",
            "MALLORY"
        ) + body
    }
}
