package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class EiscoSciLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Eisco Sci"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()
        return text.contains("EISCOLLC") &&
                (
                        text.contains("HONEOYEFALLS") ||
                                text.contains("PO-") ||
                                text.contains("QUAKERMEETINGHOUSE")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("EISCOLLC")) score += 100
        if (text.contains("HONEOYEFALLS")) score += 60
        if (text.contains("QUAKERMEETINGHOUSE")) score += 60
        if (text.contains("PO-")) score += 40
        if (text.contains("PAYMENTTERMS")) score += 20
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findShipTo(textLines)

        val rawCustomerName = shipTo.shipToCustomer
            ?: findCustomerName(textLines)

        return ParsedPdfFields(
            customerName = rawCustomerName?.let(::normalizeCustomerName),
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer?.let(::normalizeCustomerName),
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findItems(textLines)
        )
    }

    private fun findCustomerName(lines: List<String>): String? {
        return lines.firstOrNull { normalize(it) == "EISCOLLC" }
            ?.let { "EISCO LLC" }
    }

    private fun findOrderNumber(lines: List<String>): String? {
        return findFirstMatch(
            lines,
            Regex("""\b(PO-\d{4,})\b""", RegexOption.IGNORE_CASE)
        ) ?: findFirstMatch(
            lines,
            Regex("""ORDER#\s*(PO-\d{4,})""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(lines: List<String>): String? {
        val idx = lines.indexOfFirst { normalize(it).contains("PAYMENTTERMS") }
        if (idx >= 0 && idx + 1 < lines.size) {
            val next = lines[idx + 1].trim()
            if (normalize(next) == "NET30") return "Net 30"
        }

        return lines.firstOrNull { normalize(it) == "NET30" }?.let { "Net 30" }
    }

    private fun extractRightSideAddressLine(line: String): String? {
        val matches = Regex("""\d+\s*[A-Za-z]+(?:[A-Za-z\s]+)?Rd""", RegexOption.IGNORE_CASE)
            .findAll(line)
            .map { it.value }
            .toList()

        return matches.lastOrNull()?.let { extractEiscoAddressLine(it) }
    }

    private fun extractRightSideCityState(line: String): String? {
        val matches = Regex("""([A-Za-z]+(?:\s+[A-Za-z]+)?),\s*([A-Z]{2})""", RegexOption.IGNORE_CASE)
            .findAll(line)
            .toList()

        val last = matches.lastOrNull() ?: return null
        val city = last.groupValues[1]
            .replace("HoneoyeFalls", "Honeoye Falls", ignoreCase = true)
            .trim()
            .uppercase()
        val state = last.groupValues[2].trim().uppercase()

        return "$city $state"
    }
    private fun findShipTo(lines: List<String>): ShipToBlock {
        val markerIdx = lines.indexOfFirst {
            normalize(it).contains("SHIP-TOADDRESS") && normalize(it).contains("EISCOLLC")
        }

        val shipToMarkerLine = if (markerIdx >= 0) lines[markerIdx] else null

        val shipToCustomer = shipToMarkerLine?.let {
            extractAfter(it, "ship-toaddress")
        }?.let(::normalizeCustomerName)

        val addressLine1 = if (markerIdx >= 0 && markerIdx + 1 < lines.size) {
            extractRightSideAddressLine(lines[markerIdx + 1])
        } else {
            lines.firstOrNull {
                normalize(it).contains("QUAKERMEETINGHOUSE")
            }?.let { extractEiscoAddressLine(it) }
        }

        val cityStateLine = if (markerIdx >= 0 && markerIdx + 2 < lines.size) {
            extractRightSideCityState(lines[markerIdx + 2])
        } else {
            lines.firstOrNull {
                normalize(it).contains("HONEOYEFALLSNY")
            }?.let { extractRightSideCityState(it) }
        }

        val zip = if (markerIdx >= 0 && markerIdx + 3 < lines.size) {
            extractLastZip(lines[markerIdx + 3])
        } else {
            lines.firstOrNull { extractLastZip(it) != null }?.let { extractLastZip(it) }
        }

        val parsed = parseCityState(cityStateLine)

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = null,
            city = parsed.city,
            state = parsed.state,
            zip = zip
        )
    }

    private fun findItems(lines: List<String>) =
        buildList {
            var i = 0

            while (i < lines.size) {
                val normalizedLine = lines[i].replace(Regex("""\s+"""), " ").trim()

                val match = matchEiscoItemLine(normalizedLine)

                if (match != null) {
                    val leftCode = match.leftCode
                    val firstDesc = match.description
                    val vendorCode = normalizeSku(match.vendorCode)
                    val quantity = match.quantity.replace(",", "").toDoubleOrNull()
                    val unitPrice = match.unitPrice.replace(",", "").toDoubleOrNull()

                    val extraDesc = mutableListOf<String>()
                    var j = i + 1

                    while (j < lines.size) {
                        val next = lines[j].replace(Regex("""\s+"""), " ").trim()
                        val nextNorm = normalize(next)

                        val startsNewItem = matchEiscoItemLine(next) != null
                        val isFooter = nextNorm.contains("SUB-TOTAL") || nextNorm.contains("TOTAL$")
                        val isHeader = nextNorm.contains("PRODUCTDESCRIPTION") || nextNorm == "CODE"

                        val isContinuation =
                            !startsNewItem &&
                                    !isFooter &&
                                    !isHeader &&
                                    next.isNotBlank()

                        if (!isContinuation) break

                        extraDesc += next
                        j++
                    }

                    val rawDescription = buildString {
                        append(firstDesc)
                        if (extraDesc.isNotEmpty()) {
                            append(", ")
                            append(extraDesc.joinToString(", "))
                        }
                    }

                    val mapperDescription = ItemMapper.getItemDescription(vendorCode).ifBlank { null }
                    val fallbackDescription = cleanVendorDescription(rawDescription, vendorCode, leftCode)

                    add(
                        item(
                            sku = vendorCode,
                            description = mapperDescription ?: fallbackDescription,
                            quantity = quantity,
                            unitPrice = unitPrice
                        )
                    )

                    i = j
                    continue
                }

                i++
            }
        }

    private fun matchEiscoItemLine(line: String): ItemLineMatch? {
        val withComma = Regex(
            """^([A-Z0-9]+)\s+(.+?),\s+([A-Z0-9-]+)\s+([\d,]+)\s+\$?(\d+(?:\.\d{1,4})?)\s+\$?([\d,]+\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(line)

        if (withComma != null) {
            return ItemLineMatch(
                leftCode = withComma.groupValues[1].trim(),
                description = withComma.groupValues[2].trim(),
                vendorCode = withComma.groupValues[3].trim(),
                quantity = withComma.groupValues[4].trim(),
                unitPrice = withComma.groupValues[5].trim()
            )
        }

        val withoutComma = Regex(
            """^([A-Z0-9]+)\s+(.+?)\s+([A-Z0-9-]+)\s+([\d,]+)\s+\$?(\d+(?:\.\d{1,4})?)\s+\$?([\d,]+\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(line)

        if (withoutComma != null) {
            return ItemLineMatch(
                leftCode = withoutComma.groupValues[1].trim(),
                description = withoutComma.groupValues[2].trim(),
                vendorCode = withoutComma.groupValues[3].trim(),
                quantity = withoutComma.groupValues[4].trim(),
                unitPrice = withoutComma.groupValues[5].trim()
            )
        }

        return null
    }

    private fun cleanVendorDescription(raw: String?, sku: String?, leftCode: String?): String? {
        val merged = raw
            ?.replace("1VIAL/BAG(3X4),", "1 VIAL/BAG (3X4)")
            ?.replace("100STRIPS/VIAL", "100 STRIPS/VIAL")
            ?.replace("Vialof100", "VIAL OF 100")
            ?.replace("TEST-STRIPS100", "TEST STRIPS, 100")
            ?.replace("TESTSTRIP-1BAG", "TEST STRIP - 1 BAG")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim(' ', ',')
            ?.ifBlank { null }

        if (merged == null) return null

        return when ((sku ?: leftCode ?: "").uppercase()) {
            "165-1VB-100", "FSC1031SL" ->
                "PTC TEST PAPERS, 1 VIAL/BAG (3X4), 100 STRIPS/VIAL"

            "180-500V-100", "LITMUSBLUE" ->
                "BLUE LITMUS PAPER, VIAL OF 100"

            "PH2844-1V-100", "WINETEST" ->
                "WINE PH TEST STRIPS, VIAL OF 100"

            "GLU-1B-100", "GLUTEST100" ->
                "GLUCOSE PLASTIC TEST STRIPS, 100"

            else -> merged
        }
    }

    private fun extractEiscoAddressLine(line: String): String? {
        val upper = line.uppercase()
        val marker = "475QUAKERMEETINGHOUSERD"
        val idx = upper.indexOf(marker)

        return if (idx >= 0) {
            "475 QUAKER MEETING HOUSE RD"
        } else {
            line
                .replace("475QuakerMeetingHouseRd", "475 Quaker Meeting House Rd")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { null }
        }
    }

    private fun cleanCityState(line: String): String {
        return line
            .replace("HoneoyeFalls,NY", "HONEOYE FALLS NY", ignoreCase = true)
            .replace("HoneoyeFalls", "HONEOYE FALLS", ignoreCase = true)
            .replace("UnitedStates", "", ignoreCase = true)
            .replace("USA", "", ignoreCase = true)
            .replace(Regex("""\b\d{5}\b"""), "")
            .replace(",", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun extractLastZip(line: String): String? {
        return Regex("""\b(\d{5})\b""")
            .findAll(line)
            .map { it.groupValues[1] }
            .lastOrNull()
    }

    private fun extractAfter(line: String, marker: String): String? {
        val idx = line.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null

        return line.substring(idx + marker.length)
            .replace("EiscoLLC", "EISCO LLC", ignoreCase = true)
            .trim()
            .ifBlank { null }
    }

    private fun parseCityState(line: String?): CityState {
        if (line.isNullOrBlank()) return CityState(null, null)

        val normalized = line
            .replace(",", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = Regex("""^(.*)\s+([A-Z]{2})$""", RegexOption.IGNORE_CASE).find(normalized)
        return if (match != null) {
            CityState(
                city = match.groupValues[1].trim().uppercase(),
                state = match.groupValues[2].trim().uppercase()
            )
        } else {
            CityState(null, null)
        }
    }

    private fun normalizeCustomerName(value: String): String {
        return value
            .replace("EiscoLLC", "EISCO LLC", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .uppercase()
    }

    private fun normalize(text: String): String =
        text.uppercase().replace(Regex("""\s+"""), "")

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityState(
        val city: String?,
        val state: String?
    )

    private data class ItemLineMatch(
        val leftCode: String,
        val description: String,
        val vendorCode: String,
        val quantity: String,
        val unitPrice: String
    )
}