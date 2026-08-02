package com.jay.parser.packaging

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackagingCsvImporterTest {

    @Test
    fun normalizesSheetSkusAndInfersVialDiameter() {
        val file = File.createTempFile("packaging-data-", ".csv").apply {
            deleteOnExit()
            writeText(
                "\uFEFFSKU,Length (in),Width (in),Height (in),Weight (lbs)\n" +
                        "CHL-1000-1V-100,,1.125,3.375,0.09\n" +
                        "240-24-810,,,,\n" +
                        "PH3060-1B-50,,3,4,0.02\n"
            )
        }

        val parsed = PackagingCsvImporter().parse(file)
        val vial = parsed.products.getValue("CHL-1000-1V-100")

        assertEquals(1.125, vial.lengthInches)
        assertTrue(vial.inferredLength)
        assertTrue(vial.hasDimensions)
        assertTrue("240-24-8X10" in parsed.products)
        assertFalse(parsed.products.getValue("PH3060-1B-50").hasDimensions)
    }
}
