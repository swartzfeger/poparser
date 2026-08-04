package com.jay.parser.packaging

import com.jay.parser.models.ExportOrderLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackagingPlannerTest {

    @Test
    fun upsizesWhenItemWouldUseMoreThanNinetyPercentOfBox() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("TEST" to ProductPackaging(10.0, 10.0, 10.0, 5.0))
            },
            boxes = listOf(
                ShippingBox(1, 10.0, 10.0, 10.0),
                ShippingBox(2, 12.0, 10.0, 10.0)
            )
        )

        val result = planner.calculate(listOf(line("TEST", quantity = 1.0)))

        assertEquals(1, result.summary.totalBoxes)
        assertTrue(result.summary.boxPlan.startsWith("1x Box 2"))
        assertEquals(1111.111, result.summary.orderPackedVolumeCubicInches)
        assertEquals("(1 @ 5 lbs, Box #2 (12 x 10 x 10) Contains Line 1)", result.summary.invoiceNote)
        assertEquals("Complete", result.summary.status)
    }

    @Test
    fun splitsBoxesAtFiftyPounds() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("HEAVY" to ProductPackaging(1.0, 1.0, 1.0, 30.0))
            }
        )

        val result = planner.calculate(listOf(line("HEAVY", quantity = 2.0)))

        assertEquals(2, result.summary.totalBoxes)
        assertTrue(result.summary.boxPlan.startsWith("2x Box 7"))
        assertEquals(60.0, result.summary.orderWeightPounds)
        assertEquals("(2 @ 30 lbs, Box #7 (7 x 5 x 4) Contains Line 1)", result.summary.invoiceNote)
    }

    @Test
    fun suppliesTentativeBoxPlanWhenOnlyWeightIsMissing() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("LIGHT" to ProductPackaging(1.0, 1.0, 1.0, null))
            }
        )

        val result = planner.calculate(listOf(line("LIGHT", quantity = 1.0)))

        assertEquals(1, result.summary.totalBoxes)
        assertNull(result.summary.orderWeightPounds)
        assertEquals("(1 @ ? lbs, Box #7 (7 x 5 x 4) Contains Line 1)", result.summary.invoiceNote)
        assertTrue(result.summary.status.contains("Missing weight: LIGHT"))
    }

    @Test
    fun listsAllOrderLinesContainedInABox() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf(
                    "FIRST" to ProductPackaging(1.0, 1.0, 1.0, 2.0),
                    "SECOND" to ProductPackaging(1.0, 1.0, 1.0, 3.0)
                )
            },
            boxes = listOf(ShippingBox(1, 2.0, 2.0, 2.0))
        )

        val result = planner.calculate(
            listOf(
                line("FIRST", quantity = 1.0),
                line("SECOND", quantity = 1.0)
            )
        )

        assertEquals("(1 @ 5 lbs, Box #1 (2 x 2 x 2) Contains Lines 1 and 2)", result.summary.invoiceNote)
    }

    @Test
    fun neverModelsNoPartialsVialPackagesAsFractionalPackages() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("TEST-500V-100" to ProductPackaging(10.0, 10.0, 10.0, 2.0))
            },
            boxes = listOf(ShippingBox(1, 12.0, 10.0, 10.0))
        )

        val result = planner.calculate(listOf(line("TEST-500V-100", quantity = 1.5)))

        assertEquals(2, result.summary.totalBoxes)
        assertEquals(2222.222, result.summary.orderPackedVolumeCubicInches)
        assertEquals(4.0, result.summary.orderWeightPounds)
        assertEquals("(2 @ 2 lbs, Box #1 (12 x 10 x 10) Contains Line 1)", result.summary.invoiceNote)
    }

    @Test
    fun packsSixTcdFlatsAsFiveInBoxFifteenAndOneInBoxFourteen() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("145-500V-100" to ProductPackaging(21.0, 14.75, 3.0, null))
            }
        )

        val result = planner.calculate(listOf(line("145-500V-100", quantity = 6.0)))

        assertEquals(2, result.summary.totalBoxes)
        assertEquals(6195.0, result.summary.orderPackedVolumeCubicInches)
        assertEquals("1x Box 14 (22 x 16 x 7); 1x Box 15 (26 x 20 x 10)", result.summary.boxPlan)
        assertEquals(
            "(1 @ ? lbs, Box #15 (26 x 20 x 10) Contains Line 1) " +
                    "(1 @ ? lbs, Box #14 (22 x 16 x 7) Contains Line 1)",
            result.summary.invoiceNote
        )
    }

    @Test
    fun requiresReviewWhenDimensionsAreMissing() {
        val planner = PackagingPlanner(
            productsProvider = {
                mapOf("UNKNOWN-SIZE" to ProductPackaging(weightPounds = 1.0))
            }
        )

        val result = planner.calculate(listOf(line("UNKNOWN-SIZE", quantity = 1.0)))

        assertNull(result.summary.totalBoxes)
        assertTrue(result.summary.status.contains("Missing dimensions: UNKNOWN-SIZE"))
    }

    @Test
    fun usesTheAuthoritativeFifteenBoxCatalog() {
        assertEquals((1..15).toList(), ShippingBoxes.all.map { it.id })
        assertEquals("16 x 14 x 18", ShippingBoxes.all.first().dimensionsLabel)
        assertEquals("26 x 20 x 10", ShippingBoxes.all.last().dimensionsLabel)
    }

    private fun line(sku: String, quantity: Double): ExportOrderLine = ExportOrderLine(
        sku = sku,
        description = sku,
        quantityRaw = quantity,
        quantityForExport = quantity,
        unitPriceReference = null,
        unitPriceResolved = 1.0,
        glAccount = "4000"
    )
}
