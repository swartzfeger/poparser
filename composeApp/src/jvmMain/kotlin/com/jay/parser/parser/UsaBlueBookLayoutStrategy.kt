package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class UsaBlueBookLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "USABLUEBOOK"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("USABLUEBOOK") &&
                (
                        text.contains("PO5197244") ||
                                text.contains("800HIGHLANDDRIVESUITE800B") ||
                                text.contains("WESTAMPTONNJ08060") ||
                                text.contains("AMM61V25") ||
                                text.contains("VENDORPART#")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("USABLUEBOOK")) score += 100
        if (text.contains("800HIGHLANDDRIVESUITE800B")) score += 80
        if (text.contains("WESTAMPTONNJ08060")) score += 70
        if (text.contains("PO5197244")) score += 80
        if (text.contains("AMM61V25")) score += 60
        if (text.contains("PH011431V100")) score += 60
        if (text.contains("PH01141B50")) score += 60
        if (text.contains("PH50901B50")) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)
        val customerName = "USABlueBook"
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean) ?: mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        val raw = Regex(
            """\bPO\s*(\d{7,10})\b""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.getOrNull(1)
            ?: Regex(
                """PURCHASE\s+ORDER\s+NUMBER.*?\b(PO\s*\d{7,10}|\d{7,10})\b""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(joined)?.groupValues?.getOrNull(1)

        val cleaned = raw
            ?.uppercase()
            ?.replace(Regex("""\s+"""), "")
            ?.trim()
            ?: return null

        return if (cleaned.startsWith("PO")) cleaned else "PO$cleaned"
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        return Regex(
            """\b(1%10D\s+N30|NET\s+\d+|COD|PREPAID)\b""",
            RegexOption.IGNORE_CASE
        ).find(joined)?.groupValues?.get(1)?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val joined = lines.joinToString(" ") {
            it.replace(Regex("""\s+"""), " ").trim()
        }
        val compactJoined = compact(joined)

        val knownShipTos = listOf(
            KnownShipTo(
                addressLine1 = "800 Highland Drive, Suite 800-B",
                addressLine2 = null,
                city = "Westampton",
                state = "NJ",
                zip = "08060"
            ),
            KnownShipTo(
                addressLine1 = "1940 W Oak Circle",
                addressLine2 = null,
                city = "Marietta",
                state = "GA",
                zip = "30062"
            ),
            KnownShipTo(
                addressLine1 = "3781 Bur Wood Drive, Dock 5-6",
                addressLine2 = null,
                city = "Waukegan",
                state = "IL",
                zip = "60085"
            ),
            KnownShipTo(
                addressLine1 = "8349 Frontage Road",
                addressLine2 = "Suite 200",
                city = "Olive Branch",
                state = "MS",
                zip = "38654"
            )
        )

        val matched = knownShipTos.firstOrNull { candidate ->
            compactJoined.contains(compact(candidate.addressLine1)) &&
                    compactJoined.contains(compact("${candidate.city}${candidate.state}${candidate.zip}"))
        } ?: knownShipTos.firstOrNull { candidate ->
            compactJoined.contains(compact(candidate.addressLine1))
        }

        return ShipToBlock(
            shipToCustomer = "USABlueBook",
            addressLine1 = matched?.addressLine1,
            addressLine2 = matched?.addressLine2,
            city = matched?.city,
            state = matched?.state,
            zip = matched?.zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        val startIndex = lines.indexOfFirst {
            val c = compact(it)
            c.contains("VENDORPART#") &&
                    c.contains("ITEM#") &&
                    c.contains("DESCRIPTION") &&
                    c.contains("EXTENSION")
        }

        if (startIndex == -1) return emptyList()

        var i = startIndex + 1
        while (i < lines.size) {
            val rawLine = lines[i].replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(rawLine)

            if (rawLine.isBlank()) {
                i++
                continue
            }

            if (compactLine.startsWith("TOTAL")) break
            if (compactLine.contains("USABLUEBOOKAUTHORIZEDSIGNATURE")) break

            val rawNextLine = lines.getOrNull(i + 1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                .orEmpty()

            val directMatch = Regex(
                """^([A-Z0-9-]+)\s+(\d{5,6})\s+(.+?)\s+(\d+(?:,\d{3})?)\s+([A-Z]{1,4}|EA|PK)\s+\$?\s*([\d,]+\.\d{2})\s+\$?\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(rawLine)

            if (directMatch != null) {
                var sku = directMatch.groupValues[1].trim().uppercase()
                val itemNumber = directMatch.groupValues[2].trim()
                var descriptionRaw = directMatch.groupValues[3].trim()
                val quantity = directMatch.groupValues[4].replace(",", "").toDoubleOrNull()
                val unitPrice = directMatch.groupValues[6].replace(",", "").toDoubleOrNull()

                if (sku.endsWith("-")) {
                    val suffix = Regex("""^(\d+)\b""").find(rawNextLine)?.groupValues?.get(1).orEmpty()
                    if (suffix.isNotBlank()) {
                        sku += suffix

                        val cleanedNextLine = rawNextLine
                            .replaceFirst(Regex("""^\Q$suffix\E\b\s*"""), "")
                            .trim()

                        if (descriptionRaw.isBlank() && cleanedNextLine.isNotBlank()) {
                            descriptionRaw = cleanedNextLine
                        }
                    }
                }

                sku = normalizeUsaBlueBookSku(sku)

                if (quantity != null && unitPrice != null) {
                    items.add(
                        item(
                            sku = sku,
                            description = ItemMapper.getItemDescription(sku).ifBlank {
                                descriptionRaw.ifBlank { sku }
                            },
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )
                }

                i++
                continue
            }

            val wrappedMatch = Regex(
                """^([A-Z0-9-]+)\s+(\d{5,6})\s+(\d+(?:,\d{3})?)\s+([A-Z]{1,4}|EA|PK)\s+\$?\s*([\d,]+\.\d{2})\s+\$?\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(rawLine)

            if (wrappedMatch != null && i + 1 < lines.size) {
                var sku = wrappedMatch.groupValues[1].trim().uppercase()
                val itemNumber = wrappedMatch.groupValues[2].trim()
                val quantity = wrappedMatch.groupValues[3].replace(",", "").toDoubleOrNull()
                val unitPrice = wrappedMatch.groupValues[5].replace(",", "").toDoubleOrNull()

                var descriptionLine = rawNextLine

                if (sku.endsWith("-")) {
                    val suffix = Regex("""^(\d+)\b""").find(rawNextLine)?.groupValues?.get(1).orEmpty()
                    if (suffix.isNotBlank()) {
                        sku += suffix
                        descriptionLine = rawNextLine
                            .replaceFirst(Regex("""^\Q$suffix\E\b\s*"""), "")
                            .trim()
                    }
                }

                sku = normalizeUsaBlueBookSku(sku)

                if (quantity != null && unitPrice != null) {
                    items.add(
                        item(
                            sku = sku,
                            description = ItemMapper.getItemDescription(sku).ifBlank {
                                descriptionLine.ifBlank { sku }
                            },
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )
                }

                i += 2
                continue
            }

            i++
        }

        return items
    }

    private fun normalizeUsaBlueBookSku(rawSku: String): String {
        return when (rawSku) {
            "AMM-6-1V-25" -> "AMM-6-1V-25"
            "PH0114-3-1V-100" -> "PH0114-3-1V-100"
            "PH0114-1B-50" -> "PH0114-1B-50"
            "PH5090-1B-50" -> "PH5090-1B-50"
            else -> rawSku
        }
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9#]"""), "")
    }

    private data class KnownShipTo(
        val addressLine1: String,
        val addressLine2: String?,
        val city: String,
        val state: String,
        val zip: String
    )

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}