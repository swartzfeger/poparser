package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class EiscoSciLayoutStrategyTest {

    @Test
    fun usesSageCustomerIdWhileKeepingShipToName() {
        val parsed = EiscoSciLayoutStrategy().parse(
            listOf(
                "EISCO LLC",
                "Order Number: PO-002857",
                "To: Ship To:",
                "EISCO",
                "475 Quaker Meeting House Rd",
                "Honeoye Falls NY 14472"
            )
        )

        assertEquals("EISCO SCI", parsed.customerName)
        assertEquals("EISCO LLC", parsed.shipToCustomer)
    }
}
