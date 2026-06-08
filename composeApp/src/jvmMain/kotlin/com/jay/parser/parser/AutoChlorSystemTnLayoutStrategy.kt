package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class AutoChlorSystemTnLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Auto-Chlor System TN"

    override fun matches(lines: List<String>): Boolean {
        val compactText = compact(lines.joinToString("\n"))

        return compactText.contains("AUTOCHLORSYSTEM") &&
                (
                        compactText.contains("MEMPHIS") ||
                                compactText.contains("POPLAR") ||
                                compactText.contains("VENDORITEMNO")
                        )
    }

    override fun score(lines: List<String>): Int {
        val compactText = compact(lines.joinToString("\n"))

        var score = 0
        if (compactText.contains("AUTOCHLORSYSTEM")) score += 100
        if (compactText.contains("MEMPHIS")) score += 50
        if (compactText.contains("POPLAR")) score += 40
        if (compactText.contains("VENDORITEMNO")) score += 35
        if (compactText.contains("1%10NET30")) score += 25

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findShipTo(textLines)

        return ParsedPdfFields(
            customerName = findCustomerName(textLines),
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findItems(textLines)
        )
    }

    private fun findCustomerName(textLines: List<String>): String? {
        val compactText = compact(textLines.joinToString(" "))

        return if (compactText.contains("AUTOCHLORSYSTEMMEMPHISPLANT200")) {
            "AUTO-CHLOR SYSTEM MEMPHIS PLANT (200)"
        } else {
            textLines.firstOrNull {
                it.contains("Auto-Chlor System", ignoreCase = true)
            }?.trim()
        }
    }

    private fun findOrderNumber(textLines: List<String>): String? {
        return findFirstMatch(
            textLines,
            Regex("""^\*?(\d{4,})\*?$""")
        ) ?: findFirstMatch(
            textLines,
            Regex("""^(\d{4,})\s+\d{1,2}/\d{1,2}/\d{4}$""")
        ) ?: findFirstMatch(
            textLines,
            Regex("""PURCHASE\s+ORDER.*?(\d{4,})""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(textLines: List<String>): String? {
        return if (compact(textLines.joinToString(" ")).contains("1%10NET30")) {
            "1% 10 NET 30"
        } else {
            null
        }
    }

    private fun findShipTo(textLines: List<String>): ShipToBlock {
        val dropShipNameIndex = textLines.indexOfFirst {
            compact(it).contains("AUTOCHLORSYSTEMLOSANGELES450")
        }

        if (dropShipNameIndex >= 0) {
            val shipToName = "AUTO-CHLOR SYSTEM LOS ANGELES (450)"

            val addressLine = textLines
                .drop(dropShipNameIndex + 1)
                .take(4)
                .firstOrNull { compact(it).contains("4512WJEFFERSONBLVD") }
                ?.let { "4512 W. JEFFERSON BLVD" }

            val cityLine = textLines
                .drop(dropShipNameIndex + 1)
                .take(5)
                .firstOrNull {
                    val c = compact(it)
                    c.contains("LOSANGELES") && c.contains("CA") && c.contains("90016")
                }

            val parsed = parseCityStateZip(cityLine)

            return ShipToBlock(
                shipToCustomer = shipToName,
                addressLine1 = addressLine,
                addressLine2 = null,
                city = parsed.city ?: "LOS ANGELES",
                state = parsed.state ?: "CA",
                zip = parsed.zip ?: "90016-4005"
            )
        }

        val customer = findCustomerName(textLines)

        return ShipToBlock(
            shipToCustomer = customer,
            addressLine1 = "746 Poplar Avenue",
            addressLine2 = null,
            city = "Memphis",
            state = "TN",
            zip = "38105"
        )
    }

    private fun findItems(textLines: List<String>) =
        buildList {
            val seen = mutableSetOf<String>()

            textLines.forEachIndexed { index, line ->
                val directParsed = parseDirectSkuLine(line)
                    ?: parseMalformedDirectSkuLine(textLines, index)

                directParsed?.let { parsed ->
                    val sku = normalizeAutoChlorSku(parsed.sku ?: return@forEachIndexed)
                    if (sku != null && seen.add(sku)) {
                        add(
                            item(
                                sku = sku,
                                description = ItemMapper.getItemDescription(sku).ifBlank {
                                    parsed.description
                                },
                                quantity = parsed.quantity,
                                unitPrice = parsed.unitPrice
                            )
                        )
                    }
                }

                val productLine = parseProductLine(line) ?: return@forEachIndexed

                val vendorSku = findVendorSku(textLines, index + 1)
                    ?: productLine.inlineSku
                    ?: return@forEachIndexed

                val sku = normalizeAutoChlorSku(vendorSku) ?: return@forEachIndexed
                if (!seen.add(sku)) return@forEachIndexed

                add(
                    item(
                        sku = sku,
                        description = ItemMapper.getItemDescription(sku).ifBlank {
                            productLine.description
                        },
                        quantity = productLine.quantity,
                        unitPrice = productLine.unitPrice
                    )
                )
            }
        }

    private fun parseDirectSkuLine(line: String): ParsedProductLine? {
        val normalized = line.replace(Regex("""\s+"""), " ").trim()

        val match = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)$""",
            RegexOption.IGNORE_CASE
        ).find(normalized) ?: return null

        return ParsedProductLine(
            inlineSku = match.groupValues[1],
            sku = match.groupValues[1],
            description = match.groupValues[2].trim(),
            quantity = parseNumber(match.groupValues[3]),
            unitPrice = parseNumber(match.groupValues[4])
        )
    }

    private fun parseMalformedDirectSkuLine(
        textLines: List<String>,
        index: Int
    ): ParsedProductLine? {
        val line = textLines.getOrNull(index)?.trim().orEmpty()
        if (line.isBlank()) return null

        val skuMatch = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\b""",
            RegexOption.IGNORE_CASE
        ).find(line) ?: return null

        if (Regex("""^R\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
            return null
        }

        val sku = skuMatch.groupValues[1]
        val extendedTotal = Regex(
            """([\d,]+\.\d{2})\s*$"""
        ).find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseNumber)
            ?: return null

        val releaseQuantity = findReleaseQuantity(textLines, index + 1) ?: return null
        if (releaseQuantity <= 0.0) return null

        val unitPrice = extendedTotal / releaseQuantity
        if (unitPrice <= 0.0) return null

        val description = line
            .removePrefix(skuMatch.value)
            .removeSuffix(
                Regex("""([\d,]+\.\d{2})\s*$""")
                    .find(line)
                    ?.value
                    .orEmpty()
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { sku }

        return ParsedProductLine(
            inlineSku = sku,
            sku = sku,
            description = description,
            quantity = releaseQuantity,
            unitPrice = unitPrice
        )
    }

    private fun findReleaseQuantity(
        textLines: List<String>,
        startIndex: Int
    ): Double? {
        for (i in startIndex until minOf(textLines.size, startIndex + 5)) {
            val line = textLines[i].trim()

            if (
                line.contains("ReleaseQty", ignoreCase = true) ||
                line.contains("Release Qty", ignoreCase = true)
            ) {
                continue
            }

            val match = Regex(
                """^([\d,]+(?:\.\d+)?)\s+\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}/\d{1,2}/\d{4}"""
            ).find(line)

            if (match != null) {
                return parseNumber(match.groupValues[1])
            }

            if (
                Regex("""^R\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                line.contains("SubTotal", ignoreCase = true)
            ) {
                break
            }
        }

        return null
    }

    private fun parseProductLine(line: String): ParsedProductLine? {
        val normalized = line.replace(Regex("""\s+"""), " ").trim()

        val match = Regex(
            """^R\d+\s+(.+?)\s+([\d,]+(?:\.\d+)?)\s+(?:VIAL|EA|EACH|PK|BX|BOX)\s+(\d+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)$""",
            RegexOption.IGNORE_CASE
        ).find(normalized) ?: return null

        return ParsedProductLine(
            inlineSku = null,
            sku = null,
            description = match.groupValues[1].trim(),
            quantity = parseNumber(match.groupValues[2]),
            unitPrice = parseNumber(match.groupValues[3])
        )
    }

    private fun findVendorSku(textLines: List<String>, startIndex: Int): String? {
        for (i in startIndex until minOf(textLines.size, startIndex + 4)) {
            val line = textLines[i]

            if (Regex("""^R\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(line.trim())) {
                return null
            }

            val match = Regex(
                """Vendor\s*ItemNo:\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ).find(line) ?: continue

            return match.groupValues[1]
                .substringBefore("(")
                .trim()
        }

        return null
    }

    private fun normalizeAutoChlorSku(rawSku: String): String? {
        var cleaned = rawSku
            .uppercase()
            .replace("VENDOR ITEMNO:", "")
            .replace("VENDORITEMNO:", "")
            .replace(Regex("""^YOUR\s*#?\s*"""), "")
            .replace(Regex("""[^A-Z0-9-]"""), "")
            .trim('-')

        if (cleaned.isBlank()) return null

        cleaned = when {
            cleaned == "YOUR" -> return null
            else -> cleaned
        }

        return normalizeSku(cleaned)
    }

    private fun parseNumber(value: String): Double? {
        return value.replace(",", "").toDoubleOrNull()
    }

    private fun parseCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val cleaned = line
            .replace(",", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = Regex(
            """^(.*)\s+([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim(),
                state = match.groupValues[2].uppercase(),
                zip = match.groupValues[3]
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9%]"""), "")
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class ParsedProductLine(
        val inlineSku: String?,
        val sku: String?,
        val description: String,
        val quantity: Double?,
        val unitPrice: Double?
    )
}
