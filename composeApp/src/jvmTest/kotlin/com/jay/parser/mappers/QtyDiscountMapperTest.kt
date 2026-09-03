package com.jay.parser.mappers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QtyDiscountMapperTest {

    @Test
    fun preservesThreeDecimalPrecisionForDiscountedPrices() {
        val result = QtyDiscountMapper.applyQtyDiscount(
            customerId = "DIVERSIFIED FOODSERV",
            sku = "PH3060-1B-50",
            quantity = 100.0,
            unitPrice = 2.975,
            priceLevel = "DIST - 15%"
        )

        assertEquals(0.05, result.discountPercent)
        assertEquals(2.826, result.unitPrice)
    }

    @Test
    fun recognizesSingularAndPluralAllCustomerLabels() {
        assertTrue(QtyDiscountMapper.isAllCustomers("All Customer"))
        assertTrue(QtyDiscountMapper.isAllCustomers("ALL CUSTOMERS"))
    }

    @Test
    fun includesPhenomuneBulkTasteTestDiscountsFromMasterList() {
        val rule = QtyDiscountMapper.defaultRules().single {
            it.customerId == "PHENOMUNE" && it.itemId == "BULK TASTE TESTS"
        }

        assertEquals("PHENOMUNE", rule.qtyDiscountId)
        assertEquals("DIST + 100%", rule.priceLevel)
        assertEquals(listOf(100.0, 300.0, 500.0), rule.breaks.map { it.minQty })
        assertEquals(listOf(0.05, 0.15, 0.25), rule.breaks.map { it.discountPercent })
    }

    @Test
    fun matchesBartovationRulesThroughItemQuantityDiscountIds() {
        val quire = QtyDiscountMapper.applyQtyDiscount(
            customerId = "BARTOVATION LLC",
            sku = "BART-280-Q810",
            quantity = 20.0,
            unitPrice = 12.85,
            priceLevel = "DIST - 15%"
        )
        val paa = QtyDiscountMapper.applyQtyDiscount(
            customerId = "BARTOVATION LLC",
            sku = "PAA-1000-1V-50",
            quantity = 250.0,
            unitPrice = 5.14,
            priceLevel = "DIST - 15%"
        )
        val customChlorine = QtyDiscountMapper.applyQtyDiscount(
            customerId = "BARTOVATION LLC",
            sku = "SPC-CHL300-1V-100",
            quantity = 500.0,
            unitPrice = 4.05,
            priceLevel = "DIST - 15%"
        )

        assertEquals(0.10, quire.discountPercent)
        assertEquals(11.565, quire.unitPrice)
        assertEquals(0.03, paa.discountPercent)
        assertEquals(4.986, paa.unitPrice)
        assertEquals(0.05, customChlorine.discountPercent)
        assertEquals(3.848, customChlorine.unitPrice)
    }
}
