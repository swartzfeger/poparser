package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class InterconChemicalLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "INTERCON CHEMICAL"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("INTERCON CHEMICAL COMPANY") &&
                text.contains("P.O. NUMBER") &&
                text.contains("1100 CENTRAL INDUSTRIAL") &&
                text.contains("ST. LOUIS, MO 63110")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("INTERCON CHEMICAL COMPANY")) score += 120
        if (text.contains("P.O. NUMBER")) score += 80
        if (text.contains("1100 CENTRAL INDUSTRIAL")) score += 70
        if (text.contains("ST. LOUIS, MO 63110")) score += 60
        if (text.contains("1%10 NET 30")) score += 30
        if (text.contains("DELIVER TO DOORS 11-14")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.trim() }

        return ParsedPdfFields(
            customerName = "INTERCON CHEMICAL COMPANY",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "INTERCON CHEMICAL COMPANY",
            addressLine1 = "1100 CENTRAL INDUSTRIAL DRIVE",
            addressLine2 = null,
            city = "ST. LOUIS",
            state = "MO",
            zip = "63110",
            terms = "1%10 NET 30",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""P\.?\s*O\.?\s*Number:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        Regex("""\b(59\d{3})\b""")
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val rowMatch = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+(\d{1,2}/\d{1,2}/\d{4})\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{4})\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            val firstDesc = rowMatch.groupValues[2].trim()
            val quantity = rowMatch.groupValues[4].replace(",", "").toDoubleOrNull()
            val unitPrice = rowMatch.groupValues[5].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            var sku: String? = null
            val descParts = mutableListOf(firstDesc)

            for (j in (i + 1) until minOf(i + 5, lines.size)) {
                val next = lines[j].replace(Regex("""\s+"""), " ").trim()

                if (next.equals("STL EACH EACH", true)) continue

                val skuMatch = Regex("""#\s*([A-Z0-9-]+)\s*PR\b""", RegexOption.IGNORE_CASE).find(next)
                if (skuMatch != null) {
                    sku = repairSku(skuMatch.groupValues[1])
                    continue
                }

                if (next.equals("DELIVERTODOORS11-14", true) || next.equals("DELIVER TO DOORS 11-14", true)) {
                    break
                }

                if (next.isNotBlank() &&
                    !next.contains("Sub-Total", true) &&
                    !next.contains("Order Total", true)
                ) {
                    descParts.add(next)
                }
            }

            if (sku.isNullOrBlank()) continue

            val description = ItemMapper.getItemDescription(sku).ifBlank {
                cleanupDescription(descParts.joinToString(" "))
            }

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun repairSku(raw: String): String {
        return raw
            .replace(" ", "")
            .replace("\"", "")
            .uppercase()
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .replace("CHLORINETESTSTRIPS", "CHLORINE TEST STRIPS")
            .replace("QUAT(QAC)TESTSTRIPSQR", "QUAT (QAC) TEST STRIPS QR")
            .replace("LOT&EXPDATEONVIAL", "")
            .replace("LOT&EXPONVIAL", "")
            .trim(' ', '"')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("P U R C H A S E O R D E R", "PURCHASE ORDER")
            .replace("P.O.NUMBER", "P.O. NUMBER")
            .replace("INTERCONCHEMICALCOMPANY", "INTERCON CHEMICAL COMPANY")
            .replace("ST.LOUIS,MO63110", "ST. LOUIS, MO 63110")
            .replace("1100CENTRALINDUSTRIALDRIVE", "1100 CENTRAL INDUSTRIAL DRIVE")
            .replace("1100CENTRALINDUSTRIALDR", "1100 CENTRAL INDUSTRIAL DR")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}