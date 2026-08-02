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
        assertTrue(result.summary.status.contains("Missing weight: LIGHT"))
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
