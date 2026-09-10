package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChosunMeasurementLayoutStrategyTest {

    private val strategy = ChosunMeasurementLayoutStrategy()

    @Test
    fun generatesOrderNumberFromDocumentDate() {
        val expectedByDate = mapOf(
            "2026.05.01" to "CHO050126",
            "2026.05.26" to "CHO052626",
            "2026.06.30" to "CHO063026",
            "2026.08.04" to "CHO080426",
            "2026-09-10" to "CHO091026"
        )

        for ((date, expectedOrderNumber) in expectedByDate) {
            val parsed = strategy.parse(baseLines("ATTN: Mr. Lu Fisher Date: $date"))
            assertEquals(expectedOrderNumber, parsed.orderNumber, date)
        }
    }

    @Test
    fun parsesAndEnrichesProductAndLabelRows() {
        val lines = baseLines(
            "ATTN: Mr. Lu Fisher Date: 2026.08.04",
            "1 PH0114-1B-1000 50",
            "pH 1-14 PLASTIC STRIPS, 1 BAG (6X9 CLR), 1000 STRIPS/BAG",
            "2 LABELS PH0114 1000"
        )

        assertTrue(strategy.matches(lines))

        val parsed = strategy.parse(lines)
        assertEquals("CHOSUN MEASUREMENT", parsed.customerName)
        assertEquals("CHO080426", parsed.orderNumber)
        assertEquals("CHOSUN MEASUREMENT", parsed.shipToCustomer)
        assertEquals("2nd Floor 20, Muhakbong 15-gil", parsed.addressLine1)
        assertEquals("Seongdong-gu", parsed.addressLine2)
        assertEquals("Seoul", parsed.city)
        assertEquals("Korea", parsed.state)
        assertEquals("04710", parsed.zip)
        assertEquals(listOf("PH0114-1B-1000", "PLBL"), parsed.items.map { it.sku })
        assertEquals(listOf(50.0, 1000.0), parsed.items.map { it.quantity })

        val enriched = OrderEnricher().enrich("PO_precisionlaboratories.pdf", parsed)
        assertEquals("Prepaid", enriched.termsResolved)
        assertEquals(listOf(39.5, 0.15), enriched.lines.map { it.unitPriceResolved })
        assertEquals(listOf("4050", "4210"), enriched.lines.map { it.glAccount })
    }

    @Test
    fun keepsSellablePackQuantityForFiveHundredVialSku() {
        val parsed = strategy.parse(
            baseLines(
                "ATTN: Mr. Lu Fisher Date: 2026.06.30",
                "1 145-500V-100 4"
            )
        )

        val line = OrderEnricher()
            .enrich("PO_precisionlaboratories_1.pdf", parsed)
            .lines
            .single()

        assertEquals("145-500V-100", line.sku)
        assertEquals(4.0, line.quantityRaw)
        assertEquals(4.0, line.quantityForExport)
        assertEquals(535.5, line.unitPriceResolved)
    }

    private fun baseLines(vararg body: String): List<String> {
        return listOf(
            "CHOSUN MEASUREMENT",
            "2nd Floor 20, Muhakbong 15-gil, Seongdong-gu, Seoul, Korea, Zip Code 04710, Tel: +82-2-2275-0120",
            "www.chosunshop.co.kr absbre11@naver.com",
            "PURCHASE ORDER",
            "Company Name: PRECISION LABORATORIES",
            "Email Address: From: JOOMIN KOH"
        ) + body + "SIGNATURE OF CHOSUN MEASUREMENT:"
    }
}
