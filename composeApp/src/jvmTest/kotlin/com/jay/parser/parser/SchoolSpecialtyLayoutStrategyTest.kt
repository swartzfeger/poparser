package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class SchoolSpecialtyLayoutStrategyTest {

    @Test
    fun convertsTwelveVialQuantitiesToSellablePacks() {
        val parsed = parseSchoolSpecialtyItems(
            "School Specialty LLC 8992411 1 088 16-JUL-26 1 of 1",
            "132 160-3645 EACH 16-JUL-26 \$1.1000 \$145.20 1",
            "165-12V-100 PAPERS PTC TASTE PK/100",
            "36 160-3645 EACH 16-JUL-26 \$1.1000 \$39.60 2",
            "165-12V-100 PAPERS PTC TASTE PK/100"
        )

        val order = OrderEnricher().enrich("ATT8992411.pdf", parsed)

        assertEquals("8992411", order.orderNumber)
        assertEquals(listOf(132.0, 36.0), order.lines.map { it.quantityRaw })
        assertEquals(listOf(11.0, 3.0), order.lines.map { it.quantityForExport })
    }

    @Test
    fun convertsLargeVialPacksButLeavesOrdinaryItemsAlone() {
        val parsed = parseSchoolSpecialtyItems(
            "School Specialty LLC 8872491 1 088 19-MAR-26 1 of 1",
            "144 120-1111 EACH 03-JUN-26 \$0.6700 \$96.48 1",
            "180-144V-100 LITMUS PAPER BLUE PKG/100",
            "144 120-1122 EACH 03-JUN-26 \$0.6700 \$96.48 2",
            "190-144V-100 LITMUS PAPER RED PKG/100",
            "12 160-0862 EACH 03-JUN-26 \$0.8100 \$9.72 4",
            "LENS-34-50 PAPER LENS 3X4 PKG/50"
        )

        val order = OrderEnricher().enrich("ATT8872491.pdf", parsed)

        assertEquals(listOf(1.0, 1.0, 12.0), order.lines.map { it.quantityForExport })
    }

    @Test
    fun leavesOneBagOneVialAndUnmarkedItemsAlone() {
        val parsed = parseSchoolSpecialtyItems(
            "School Specialty LLC 8991216 1 075 15-JUL-26 1 of 1",
            "24 2134800 EACH 06-AUG-26 \$3.5000 \$84.00 1",
            "PH0114-1B-50 PH 1-14 PLASTIC TEST STRIPS PKG/50",
            "250 1278522 EACH 06-AUG-26 \$3.2445 \$811.13 2",
            "SPC-CHROM-50-6X75 PAPER STRIP CHROMATOGRAPHY .75X6 PK/50",
            "14 60-5104 EACH 06-AUG-26 \$7.5000 \$105.00 3",
            "PRO-1V-50 PROTEIN TEST STRIPS PK/50"
        )

        val order = OrderEnricher().enrich("ATT8991216.pdf", parsed)

        assertEquals(listOf(24.0, 250.0, 14.0), order.lines.map { it.quantityForExport })
    }

    @Test
    fun keepsPrintedQuantityWhenPoAlreadyUsesFullPackPrice() {
        val parsed = parseSchoolSpecialtyItems(
            "School Specialty LLC 9053989 1 075 27-AUG-26 1 of 1",
            "144 569867 EACH 28-SEP-26 \$8.6500 \$1,245.60 1",
            "180-12V-100 LITMUS TEST PAPER BLUE VIALS/100 PK/12",
            "11 2134798 EACH 22-SEP-26 \$13.2500 \$145.75 3",
            "135-12V-100 PH PH TEST PAPERS 100 STRIPS PK12"
        )

        val order = OrderEnricher().enrich("ATT9053989.pdf", parsed)

        assertEquals(listOf(144.0, 11.0), order.lines.map { it.quantityRaw })
        assertEquals(listOf(144.0, 11.0), order.lines.map { it.quantityForExport })
    }

    private fun parseSchoolSpecialtyItems(vararg itemLines: String) =
        SchoolSpecialtyLayoutStrategy().parse(
            listOf(
                "PO Number Rev # FC Date Page #",
                *itemLines,
                "Ship To:SCHOOL SPECIALTY LLC",
                "1300 S LYNNDALE DR",
                "APPLETON, WI 54914",
                "Email: ANGELA.BUSS@SCHOOLSPECIALTY.COM"
            )
        )
}
