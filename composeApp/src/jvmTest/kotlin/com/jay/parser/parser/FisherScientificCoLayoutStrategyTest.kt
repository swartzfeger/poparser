package com.jay.parser.parser

import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfLine
import kotlin.test.Test
import kotlin.test.assertEquals

class FisherScientificCoLayoutStrategyTest {

    @Test
    fun parsesNoisyWindowsOcrFaxRows() {
        val lines = listOf(
            "Fisher Scientific Company, LLG",
            "A Tressio Maher Scientific Brand",
            "Fisher Solantific Customer Account",
            "Supplier Information ___| Ship To Information ee | Emalt: APFax@thermetisher.com.",
            "415 AIRPARK DRIVE �6771 SILVER CREST ROAD me Box 1768",
            "tT g00 733-0266 [NAZARETH PA. 18064 | PURCHASING AGENT: |",
            "Fisher Scientific Supplier Number",
            "SUPPLIER | FISHER [ PRODUCT DESCRIPTION i ory | vos] WHIT i EXTENDED | SHIP HO |",
            "CATALOG | CATALOG i AND ADDITIONAL NOTES | i [ COST | COST |LATER THAN |",
            "| 120-127�100 | Seciz4 METHYL. VIOLET TEST PPR 12vT-EEr | 4 | PEI 13.25 | 63 00 | gaen4a-oa |",
            "| FISHER FO LINE: # 001 | | | ! | | |",
            "| 175~24V~1L00~EKDE | S6&0125 LEAD ACETATE TEST PPR 24�L-PE | 6 | PE | 25.25 | 151.50 | O3vode26 |",
            "| FISHER PO LINE: # 902 | | | | |",
            "| PHOOIS-1B-S5o | S�141 BCO PH TEST STRIP 0-1.6 S0eKE | 2 | PR 3.50 | 7.00 | O3-04-26 |",
            "| FISHER PO LINE: # a2 | | | | |",
            "TOTAL: 241.50"
        ).map { PdfLine(tokens = emptyList(), text = it) }

        val parsed = PdfFieldParser().parse(lines)

        assertEquals("FISHER SCIENTIFIC CO", parsed.customerName)
        assertEquals("FISHER SCIENTIFIC COMPANY", parsed.shipToCustomer)
        assertEquals("6771 SILVER CREST ROAD", parsed.addressLine1)
        assertEquals("NAZARETH", parsed.city)
        assertEquals("PA", parsed.state)
        assertEquals("18064", parsed.zip)
        assertEquals(3, parsed.items.size)
        assertEquals("120-12V-100", parsed.items[0].sku)
        assertEquals(4.0, parsed.items[0].quantity)
        assertEquals(13.25, parsed.items[0].unitPrice)
        assertEquals("175-24V-100", parsed.items[1].sku)
        assertEquals(6.0, parsed.items[1].quantity)
        assertEquals(25.25, parsed.items[1].unitPrice)
        assertEquals("PH0015-1B-50", parsed.items[2].sku)
        assertEquals(2.0, parsed.items[2].quantity)
        assertEquals(3.50, parsed.items[2].unitPrice)
    }
}
