package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SchoolSpecialtyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "SCHOOL SPECIALTY"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("SCHOOL SPECIALTY LLC") &&
                text.contains("PO NUMBER") &&
                text.contains("ANGELA.BUSS@SCHOOLSPECIALTY.COM")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("SCHOOL SPECIALTY LLC")) score += 120
        if (text.contains("PO NUMBER")) score += 80
        if (text.contains("ANGELA.BUSS@SCHOOLSPECIALTY.COM")) score += 60
        if (text.contains("1%10 NET 30")) score += 40
        if (text.contains("SSI_DOMESTIC_PO")) score += 30
        if (text.contains("100 PARAGON PARKWAY")) score += 50
        if (text.contains("1300 S LYNNDALE DR")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "SCHOOL SPECIALTY LLC",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "SCHOOL SPECIALTY LLC",
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = "1%10 NET 30",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val match = Regex(
                """School Specialty LLC\s+(\d{7})\s+\d+\s+\d+\s+[\dA-Z-]+\s+\d+\s+of\s+\d+""",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (match != null) return match.groupValues[1]
        }

        val joined = lines.joinToString("\n")

        Regex("""\b(88\d{5})\b""")
            .find(joined)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val joined = normalize(lines.joinToString("\n"))

        return when {
            joined.contains("100 PARAGON PARKWAY") && joined.contains("MANSFIELD, OH 44903") ->
                ShipToBlock(
                    addressLine1 = "100 PARAGON PARKWAY",
                    city = "MANSFIELD",
                    state = "OH",
                    zip = "44903"
                )

            joined.contains("1300 S LYNNDALE DR") && joined.contains("APPLETON, WI 54914") ->
                ShipToBlock(
                    addressLine1 = "1300 S LYNNDALE DR",
                    city = "APPLETON",
                    state = "WI",
                    zip = "54914"
                )

            else ->
                ShipToBlock(
                    addressLine1 = null,
                    city = null,
                    state = null,
                    zip = null
                )
        }
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < lines.size - 1) {
            val row1 = lines[i].replace(Regex("""\s+"""), " ").trim()
            val row2 = lines[i + 1].replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^([\d,]+\s*)\s+([A-Z0-9-]+)\s+([A-Z]+)\s+(\d{2}-[A-Z]{3}-\d{2})\s+\$([\d,]+\.\d{4})\s+\$([\d,]+\.\d{2})\s+(\d+)$""",
                RegexOption.IGNORE_CASE
            ).find(row1)

            if (match != null) {
                val quantity = match.groupValues[1]
                    .replace(",", "")
                    .trim()
                    .toDoubleOrNull()

                val ourItem = match.groupValues[2].trim()
                val unitPrice = match.groupValues[5].replace(",", "").toDoubleOrNull()

                val row2Match = Regex(
                    """^([A-Z0-9-]+)\s+(.+)$""",
                    RegexOption.IGNORE_CASE
                ).find(row2)

                val sku = row2Match?.groupValues?.get(1)?.trim()?.uppercase()
                val rawDescription = row2Match?.groupValues?.get(2)?.trim()

                if (sku != null && quantity != null && unitPrice != null) {
                    val description = ItemMapper.getItemDescription(sku).ifBlank {
                        cleanupDescription(rawDescription ?: ourItem)
                    }

                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = description,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                i += 2
                continue
            }

            i++
        }

        return items
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("SCHOOLSPECIALTYLLC", "SCHOOL SPECIALTY LLC")
            .replace("PONUMBER", "PO NUMBER")
            .replace("1%10NET30", "1%10 NET 30")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private data class ShipToBlock(
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}