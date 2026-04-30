package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class HomeScienceToolsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "HOME SCIENCE TOOLS"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("HOMESCIENCETOOLS") ||
                text.contains("PO@HOMESCIENCETOOLS.COM") ||
                (text.contains("ORDERNO.:") && text.contains("SHIPTO:")) ||
                text.contains("665CARBONST")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("HOMESCIENCETOOLS")) score += 120
        if (text.contains("PO@HOMESCIENCETOOLS.COM")) score += 80
        if (text.contains("665CARBONST")) score += 70
        if (text.contains("BILLINGS,MT,59102-6451")) score += 70
        if (text.contains("BLANKETPONBR")) score += 30
        if (text.contains("GROUND-UPS")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "HOME SCIENCE TOOLS",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""ORDER\s*NO\.\:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("NET 30", ignoreCase = true) -> "Net 30"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { normalize(it).contains("TO:SHIPTO:") }
        if (idx >= 0) {
            val line1 = lines.getOrNull(idx + 1).orEmpty()
            val line2 = lines.getOrNull(idx + 2).orEmpty()
            val line3 = lines.getOrNull(idx + 3).orEmpty()

            val shipToCustomer = splitToShipToLine(line1).second ?: "Home Science Tools"
            val addressLine1 = splitToShipToLine(line2).second ?: "665 CARBON ST"
            val cityStateZip = splitToShipToLine(line3).second ?: "BILLINGS MT 59102-6451"
            val parsed = parseCityStateZip(cityStateZip)

            return ShipToBlock(
                shipToCustomer = shipToCustomer,
                addressLine1 = addressLine1,
                city = parsed?.city,
                state = parsed?.state,
                zip = parsed?.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = "Home Science Tools",
            addressLine1 = "665 CARBON ST",
            city = "BILLINGS",
            state = "MT",
            zip = "59102-6451"
        )
    }

    private fun splitToShipToLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val vendorPrefixes = listOf(
            "PRECISION LABORATORIES",
            "415 S AIRPARK RD",
            "COTTONWOOD AZ 86326-4050",
            "United States of America"
        )

        for (prefix in vendorPrefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val right = trimmed.removePrefix(prefix).trim()
                return prefix to right.ifBlank { null }
            }
        }

        val parts = Regex("""\s{2,}""").split(trimmed).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> null to trimmed
        }
    }

    private fun parseCityStateZip(text: String?): CityStateZip? {
        if (text.isNullOrBlank()) return null

        val cleaned = text
            .replace("BILLINGSMT", "BILLINGS MT")
            .trim()

        val match = Regex("""^([A-Z .'-]+)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            val match = Regex(
                """^(\d+)\s+([\d.]+)\s+([A-Z0-9-]+):\s+(.+?)(EA|PK\d+)\s+([\d.]+)\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (match != null) {
                val quantity = match.groupValues[2].toDoubleOrNull()
                val firstDescription = match.groupValues[4].trim().trimEnd(',', ' ')
                val uom = match.groupValues[5].trim()
                val unitPrice = match.groupValues[6].toDoubleOrNull()

                if (quantity == null || unitPrice == null) {
                    i++
                    continue
                }

                val line2 = lines.getOrNull(i + 1)?.trim().orEmpty()
                val line3 = lines.getOrNull(i + 2)?.trim().orEmpty()
                val line4 = lines.getOrNull(i + 3)?.trim().orEmpty()

                val skuMatch = Regex("""^([A-Z0-9-]+):\s+(.+)$""", RegexOption.IGNORE_CASE).find(line3)
                val sku = skuMatch?.groupValues?.get(1)?.trim()?.uppercase()
                val secondDescription = skuMatch?.groupValues?.get(2)?.trim().orEmpty()

                val extraDescription = if (line4.isNotBlank() && !looksLikeFooter(line4)) line4 else ""

                if (!sku.isNullOrBlank()) {
                    val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { "" }
                    val description = if (mappedDescription.isNotBlank()) {
                        mappedDescription
                    } else {
                        listOf(firstDescription, line2, secondDescription, extraDescription)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
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

                    i += if (extraDescription.isNotBlank()) 3 else 2
                }
            }

            i++
        }

        return items
    }

    private fun looksLikeFooter(line: String): Boolean {
        val text = normalize(line)
        return text.startsWith("POTOTAL:") ||
                text.startsWith("PLEASECONFIRMHSTPURCHASEORDER") ||
                text.startsWith("**SENDTRACKINGNUMBER") ||
                text.startsWith("TAXTOTAL:") ||
                text.startsWith("TOTAL(USD):") ||
                text.startsWith("PAGE:")
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )
}