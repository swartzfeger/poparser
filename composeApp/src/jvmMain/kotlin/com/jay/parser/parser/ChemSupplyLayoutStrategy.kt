package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class ChemSupplyLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "ChemSupply"

    override fun matches(lines: List<String>): Boolean {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()

        return joined.contains("CHEMSUPPLY AUSTRALIA") &&
                joined.contains("PURCHASE ORDER") &&
                joined.contains("ORDER NO")
    }

    override fun score(lines: List<String>): Int {
        val joined = nonBlankLines(lines).joinToString("\n").uppercase()
        var score = 0

        if (joined.contains("CHEMSUPPLY")) score += 40
        if (joined.contains("PURCHASE ORDER")) score += 20
        if (joined.contains("ORDER NO")) score += 20
        if (joined.contains("DELIVER TO")) score += 20
        if (joined.contains("SUPPLIER ITEM CODE")) score += 10

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        val orderNumber = findFirstMatch(
            textLines,
            Regex("""Order No:\s*(\d+)""", RegexOption.IGNORE_CASE)
        )

        val terms = findOrderTerms(textLines)
        val shipTo = findChemSupplyShipTo(textLines)
        val items = findChemSupplyItems(textLines)

        return ParsedPdfFields(
            customerName = "CHEMSUPPLY AUSTRALIA PTY LTD",
            orderNumber = orderNumber,
            shipToCustomer = "CHEMSUPPLY AUSTRALIA PTY LTD",
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = terms,
            items = items
        )
    }

    private fun findOrderTerms(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]

            val inlineMatch = Regex("""Order Terms\s+(.+)""", RegexOption.IGNORE_CASE).find(line)
            if (inlineMatch != null) {
                val value = inlineMatch.groupValues[1].trim()
                if (value.isNotBlank() && !value.equals("Required By", ignoreCase = true)) {
                    return value
                }
            }

            if (line.equals("Order Terms Required By", ignoreCase = true) && i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                val match = Regex("""^(.+?)\s+\d{1,2}-[A-Z]{3}-\d{2}$""", RegexOption.IGNORE_CASE).find(next)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
                if (next.isNotBlank()) {
                    return next
                }
            }
        }

        return null
    }

    private fun findChemSupplyShipTo(lines: List<String>): InterpretedShipTo {
        var addressLine1: String? = null
        var city: String? = null
        var zip: String? = null
        var state: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.contains("BEDFORD STREET", ignoreCase = true)) {
                val cleaned = trimmed
                    .replace(Regex("""^PRECISION LABORATORIES\s+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+[A-Z0-9]{4,}\s+\S+\s+USD$""", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (cleaned.isNotBlank()) {
                    addressLine1 = cleaned
                }
            }

            if (trimmed.contains("GILLMAN", ignoreCase = true)) {
                val cleaned = trimmed
                    .replace(Regex("""^COTTONWOOD\s+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+Order Terms Required By$""", RegexOption.IGNORE_CASE), "")
                    .trim()

                val match = Regex("""^(.+?)\s+(\d{4})$""").find(cleaned)
                if (match != null) {
                    city = match.groupValues[1].trim()
                    zip = match.groupValues[2].trim()
                } else if (cleaned.isNotBlank()) {
                    city = cleaned
                }
            }

            if (trimmed.contains("SOUTH AUSTRALIA", ignoreCase = true)) {
                val cleaned = trimmed
                    .replace(Regex("""^ARIZONA\s+86326\s+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+\d+\s+Days\s+from\s+Invoice\s+\d{1,2}-[A-Z]{3}-\d{2}$""", RegexOption.IGNORE_CASE), "")
                    .trim()

                if (cleaned.isNotBlank()) {
                    state = cleaned
                }
            }
        }

        return InterpretedShipTo(
            shipToCustomer = "CHEMSUPPLY AUSTRALIA PTY LTD",
            addressLine1 = addressLine1,
            addressLine2 = null,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun findChemSupplyItems(lines: List<String>) =
        buildList {
            val headerIndex = lines.indexOfFirst {
                it.uppercase().contains("ITEM CODE") &&
                        it.uppercase().contains("SUPPLIER ITEM CODE") &&
                        it.uppercase().contains("ITEM DESCRIPTION")
            }

            if (headerIndex == -1) return@buildList

            val singleLineRowRegex = Regex(
                """^([A-Z0-9-]+)\s+([A-Z0-9./-]+)\s+(.+?)\s+([\d.]+)\s+([A-Z]+|\d+)\s+([\d.]+)\s+([\d.]+)$""",
                RegexOption.IGNORE_CASE
            )

            val continuationLineRegex = Regex(
                """^([\d.]+)\s+([A-Z]+|\d+)\s+([\d.]+)\s+([\d.]+)$""",
                RegexOption.IGNORE_CASE
            )

            var pendingItemCode: String? = null
            var pendingSku: String? = null
            val pendingDescription = mutableListOf<String>()

            fun flushPending(
                quantity: Double,
                unitPrice: Double
            ) {
                val sku = normalizeSku(pendingSku.orEmpty())
                val descriptionFromCatalog = ItemMapper.getItemDescription(sku).ifBlank { null }
                val description = descriptionFromCatalog ?: pendingDescription.joinToString(" ").trim().ifBlank { null }

                add(
                    item(
                        sku = sku.ifBlank { null },
                        description = description,
                        quantity = quantity,
                        unitPrice = unitPrice
                    )
                )

                pendingItemCode = null
                pendingSku = null
                pendingDescription.clear()
            }

            for (i in headerIndex + 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                val upper = line.uppercase()
                if (
                    upper.startsWith("PHONE:") ||
                    upper.startsWith("FAX:") ||
                    upper.startsWith("CONDITION OF ORDER") ||
                    upper.startsWith("WAREHOUSE") ||
                    upper.startsWith("ORDER TERMS") ||
                    upper.startsWith("DELIVERY INSTRUCTIONS") ||
                    upper.startsWith("TOTAL ")
                ) {
                    break
                }

                val single = singleLineRowRegex.find(line)
                if (single != null) {
                    val sku = normalizeSku(single.groupValues[2].trim())
                    val quantity = single.groupValues[4].toDoubleOrNull()
                    val unitPrice = single.groupValues[6].toDoubleOrNull()

                    if (quantity != null && unitPrice != null) {
                        val descriptionFromCatalog = ItemMapper.getItemDescription(sku).ifBlank { null }
                        val description = descriptionFromCatalog ?: single.groupValues[3].trim().ifBlank { null }

                        add(
                            item(
                                sku = sku,
                                description = description,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                    continue
                }

                val startsNewItem = Regex("""^([A-Z0-9-]+)\s+([A-Z0-9./-]+)\s+(.+)$""", RegexOption.IGNORE_CASE)
                    .find(line)

                if (startsNewItem != null) {
                    pendingItemCode = startsNewItem.groupValues[1].trim()
                    pendingSku = startsNewItem.groupValues[2].trim()
                    pendingDescription.clear()
                    pendingDescription.add(startsNewItem.groupValues[3].trim())
                    continue
                }

                val continuation = continuationLineRegex.find(line)
                if (continuation != null && pendingSku != null) {
                    val quantity = continuation.groupValues[1].toDoubleOrNull()
                    val unitPrice = continuation.groupValues[3].toDoubleOrNull()

                    if (quantity != null && unitPrice != null) {
                        flushPending(quantity, unitPrice)
                    }
                    continue
                }

                if (pendingSku != null) {
                    pendingDescription.add(line)
                }
            }
        }
}