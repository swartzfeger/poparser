package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class MedlineLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "Medline"

    override fun matches(lines: List<String>): Boolean {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()
        return joined.contains("MEDLINE") &&
                joined.contains("YOUR MATERIAL NUMBER") &&
                joined.contains("MEDLINE PO NUMBER")
    }

    override fun score(lines: List<String>): Int {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()
        var score = 0
        if (joined.contains("MEDLINE")) score += 25
        if (joined.contains("YOUR MATERIAL NUMBER")) score += 35
        if (joined.contains("MEDLINE PO NUMBER")) score += 25
        if (joined.contains("SHIP-TO")) score += 20
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findMedlineShipTo(textLines)

        return ParsedPdfFields(
            customerName = findCustomerName(textLines),
            orderNumber = findFirstMatch(
                textLines,
                Regex("""MEDLINE PO NUMBER\s*:?\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
            ),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findFirstMatch(
                textLines,
                Regex("""Terms\s*(?:del\.)?\s*:?\s*(.+)$""", RegexOption.IGNORE_CASE)
            ),
            items = findMedlineItems(textLines)
        )
    }

    private fun findCustomerName(lines: List<String>): String {
        val withPurchaseOrder = lines.firstOrNull {
            val upper = it.uppercase()
            upper.contains("MEDLINE INDUSTRIES") && upper.contains("PURCHASE ORDER")
        }

        if (withPurchaseOrder != null) {
            return withPurchaseOrder
                .replace(Regex("""\bPurchase order\b""", RegexOption.IGNORE_CASE), "")
                .trim()
                .replace(Regex("""\s+"""), " ")
        }

        return lines.firstOrNull { it.uppercase().contains("MEDLINE INDUSTRIES") }
            ?.trim()
            ?: "MEDLINE INDUSTRIES"
    }

    private fun findMedlineShipTo(lines: List<String>): InterpretedShipTo {
        val shipToIndex = lines.indexOfFirst {
            it.contains("SHIP-TO", ignoreCase = true) || it.contains("SHIP TO", ignoreCase = true)
        }
        if (shipToIndex == -1) return InterpretedShipTo()

        val block = mutableListOf<String>()

        for (i in (shipToIndex + 1)..lines.lastIndex) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val upper = line.uppercase()

            if (upper.matches(Regex("""^[*=_\- ]{5,}$"""))) break
            if (upper == "US") continue
            if (upper.contains("BILL TO ABOVE ADDRESS")) continue
            if (upper.contains("PRECISION LABORATORIES")) continue
            if (upper.contains("AIRPARK")) continue
            if (upper.contains("COTTONWOOD")) continue
            if (upper.contains("VENDOR #")) continue

            block.add(line)
            if (block.size == 4) break
        }

        if (block.isEmpty()) return InterpretedShipTo()

        val cityStateZipLine = block.firstOrNull { line ->
            Regex("""([A-Za-z .,'/-]+)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")
                .containsMatchIn(line.trim())
        }

        val match = cityStateZipLine?.let { line ->
            Regex("""([A-Za-z .,'/-]+)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)""")
                .find(line.trim())
        }

        val city = match?.groupValues?.getOrNull(1)?.trim()?.trimEnd(',')
        val state = match?.groupValues?.getOrNull(2)?.trim()
        val zip = match?.groupValues?.getOrNull(3)?.trim()

        val nonCityLines = block.filter { it != cityStateZipLine }

        return InterpretedShipTo(
            shipToCustomer = cleanShipToLine(nonCityLines.getOrNull(0)),
            addressLine1 = cleanShipToLine(nonCityLines.getOrNull(1)),
            addressLine2 = cleanShipToLine(nonCityLines.getOrNull(2)),
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun cleanShipToLine(line: String?): String? {
        if (line == null) return null

        return line
            .replace(Regex("""\bMEDLINE PO NUMBER\s*:.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bPO NUMBER\s*:.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bDATE\s*:.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .ifBlank { null }
    }

    private fun findMedlineItems(lines: List<String>) =
        buildList {
            val materialRegex = Regex(
                """Your material number\s+([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            )
            val qtyPriceRegex = Regex(
                """^(\d+(?:\.\d+)?)\s+pack\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$""",
                RegexOption.IGNORE_CASE
            )

            for (i in lines.indices) {
                val materialMatch = materialRegex.find(lines[i]) ?: continue
                val sku = normalizeSku(materialMatch.groupValues[1].trim())

                val nearby = listOfNotNull(
                    lines.getOrNull(i - 2),
                    lines.getOrNull(i - 1),
                    lines.getOrNull(i + 1),
                    lines.getOrNull(i + 2)
                )

                val qtyMatch = nearby.firstNotNullOfOrNull { qtyPriceRegex.find(it) }

                add(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku).ifBlank { null },
                        quantity = qtyMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                        unitPrice = qtyMatch?.groupValues?.getOrNull(2)?.toDoubleOrNull()
                    )
                )
            }
        }
}