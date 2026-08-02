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
}
