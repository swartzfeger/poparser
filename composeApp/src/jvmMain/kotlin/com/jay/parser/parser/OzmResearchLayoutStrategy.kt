package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class OzmResearchLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("CZ25278118") &&
                text.contains("KLARAHEJKRLIKOVA") &&
                text.contains("CHIEFMARKETINGOFFICER") &&
                text.contains("ABELHEATTEST") &&
                text.contains("CERTIFICATESOFCONFORMANCEREQUIRED") &&
                text.contains("2202002070")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("091126OZMKHPRECISIONLABS")) score += 240
        if (text.contains("CZ25278118")) score += 220
        if (text.contains("KLARAHEJKRLIKOVA")) score += 200
        if (text.contains("ABELHEATTEST")) score += 180
        if (text.contains("CERTIFICATESOFCONFORMANCEREQUIRED")) score += 160
        if (text.contains("2202002070")) score += 140
        if (text.contains("SPC160ML")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.trim()
            },
            shipToCustomer = "OZM Research s.r.o.",
            addressLine1 = "Bližňovice 32",
            city = "Hrochův Týnec",
            state = "Czech Republic",
            zip = "538 62",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = ITEM_SPECS.mapNotNull { spec ->
        val skuIndex = lines.indexOfFirst { line -> spec.printedSkuPattern.containsMatchIn(line) }
        if (skuIndex < 0) return@mapNotNull null

        val itemText = (skuIndex..minOf(skuIndex + 4, lines.lastIndex))
            .joinToString(" ") { candidateIndex -> lines[candidateIndex] }
        val quantity = spec.quantityPattern.find(itemText)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
            ?: return@mapNotNull null
        if (quantity <= 0.0) return@mapNotNull null

        item(
            sku = spec.masterSku,
            description = ItemMapper.getItemDescription(spec.masterSku).ifBlank { spec.masterSku },
            quantity = quantity,
            unitPrice = null,
            uom = spec.uom
        )
    }

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ItemSpec(
        val masterSku: String,
        val uom: String,
        val printedSkuPattern: Regex,
        val quantityPattern: Regex
    )

    private companion object {
        const val CUSTOMER_ID = "OZM RESEARCH S.R.O."

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDER\s*NO\s*:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_SPECS = listOf(
            ItemSpec(
                masterSku = "220-200-2070",
                uom = "200 STRIPS",
                printedSkuPattern = Regex("""220-200-2070\b""", RegexOption.IGNORE_CASE),
                quantityPattern = Regex(
                    """\b(\d+(?:\.\d+)?)\s*[X×]\s*200\s+STRIPS\b""",
                    RegexOption.IGNORE_CASE
                )
            ),
            ItemSpec(
                masterSku = "SPC-160-MIL",
                uom = "100 STRIPS",
                printedSkuPattern = Regex("""SPC-160-MI?L\b""", RegexOption.IGNORE_CASE),
                quantityPattern = Regex(
                    """\b(\d+(?:\.\d+)?)\s*[X×]\s*100\s+STRIPS\b""",
                    RegexOption.IGNORE_CASE
                )
            )
        )
    }
}
