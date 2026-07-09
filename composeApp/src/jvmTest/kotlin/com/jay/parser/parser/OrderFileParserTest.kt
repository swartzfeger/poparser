package com.jay.parser.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderFileParserTest {

    @Test
    fun normalizesSparseFisherPrNumberFromOcr() {
        val text = """
            Fisher Scientific Order Number
            PR41 03492
            Terms and Conditions
        """.trimIndent()

        assertEquals(
            listOf("PR4103492"),
            OrderFileParser().extractFisherPrNumbers(text)
        )
    }
}
