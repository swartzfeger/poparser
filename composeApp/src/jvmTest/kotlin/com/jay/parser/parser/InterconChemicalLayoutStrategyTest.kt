package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class InterconChemicalLayoutStrategyTest {

    @Test
    fun convertsIndividualVialsToSellablePackQuantity() {
        val parsed = InterconChemicalLayoutStrategy().parse(
            listOf(
                "INTERCON CHEMICAL COMPANY",
                "P.O. Number: 59172",
                "1100 CENTRAL INDUSTRIAL DRIVE",
                "ST. LOUIS, MO 63110",
                "EQ-TESTW-CHLR-C1TS CHLORINE TEST STRIPS (TUBE/100) 2/4/2026 288.00 1.3218 380.68",
                "STL EACH EACH",
                "#145-144V-100 PR \"LOT & EXP DATE ON VIAL\"",
                "DELIVER TO DOORS 11-14"
            )
        )

        val order = OrderEnricher().enrich("59172 pr080.pdf", parsed)
        val line = order.lines.single()

        assertEquals("INTERCON CHEMICAL CO", order.customer?.id)
        assertEquals(288.0, line.quantityRaw)
        assertEquals(2.0, line.quantityForExport)
        assertEquals(399.3, line.unitPriceResolved * line.quantityForExport, absoluteTolerance = 0.001)
        assertEquals(611.111, order.packaging.orderPackedVolumeCubicInches)
        assertEquals(1, order.packaging.totalBoxes)
    }
}
