package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class EiscoSciLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Eisco Sci"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()
        return (
                text.contains("EISCO LLC") ||
                        text.contains("EISCOLLC") ||
                        text.contains("EISCO")
                ) && (
                text.contains("HONEOYE FALLS") ||
                        text.contains("HONEOYEFALLS") ||
                        text.contains("QUAKER MEETING HOUSE") ||
                        text.contains("QUAKERMEETINGHOUSE") ||
                        text.contains("PO-") ||
                        text.contains("ORDER NUMBER:")
                )
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("EISCO LLC") || text.contains("EISCOLLC")) score += 100
        if (text.contains("HONEOYE FALLS") || text.contains("HONEOYEFALLS")) score += 60
        if (text.contains("QUAKER MEETING HOUSE") || text.contains("QUAKERMEETINGHOUSE")) score += 60
        if (text.contains("PO-")) score += 40
        if (text.contains("ORDER NUMBER:")) score += 40
        if (text.contains("PAYMENT TERMS") || text.contains("PAYMENTTERMS")) score += 20
        if (text.contains("TO: SHIP TO:")) score += 20
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = findShipTo(textLines)

        val rawCustomerName = shipTo.shipToCustomer ?: findCustomerName(textLines)

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
        return lines.firstOrNull {
            val n = normalize(it)
            n == "EISCOLLC" || n == "EISCO" || n == "EISCOLLCLLC"
        }?.let { "EISCO LLC" }
    }

    private fun findOrderNumber(lines: List<String>): String? {
        return findFirstMatch(
            lines,
            Regex("""\b(PO-\d{4,})\b""", RegexOption.IGNORE_CASE)
        ) ?: findFirstMatch(
            lines,
            Regex("""ORDER#\s*(PO-\d{4,})""", RegexOption.IGNORE_CASE)
        ) ?: findFirstMatch(
            lines,
            Regex("""ORDER\s*NUMBER[:\s]*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
        )
    }

    private fun findTerms(lines: List<String>): String? {
        val paymentTermsIdx = lines.indexOfFirst { normalize(it).contains("PAYMENTTERMS") }
        if (paymentTermsIdx >= 0 && paymentTermsIdx + 1 < lines.size) {
            val next = lines[paymentTermsIdx + 1].trim()
            if (normalize(next) == "NET30") return "Net 30"
        }

        lines.firstOrNull { normalize(it) == "NET30" }?.let { return "Net 30" }

        lines.firstOrNull {
            Regex("""NET\s+DUE\s+IN\s+30""", RegexOption.IGNORE_CASE).containsMatchIn(it) ||
                    Regex("""\bNET\s*30\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }?.let { return "Net 30" }

        return null
    }

    private fun findShipTo(lines: List<String>): ShipToBlock {
        return findShipToErpStyle(lines) ?: findShipToClassic(lines)
    }

    private fun findShipToErpStyle(lines: List<String>): ShipToBlock? {
        val markerIdx = lines.indexOfFirst {
            val n = normalize(it)
            n.contains("TO:SHIPTO:") || (n.contains("TO:") && n.contains("SHIPTO:"))
        }
        if (markerIdx < 0) return null

        val shipStart = (markerIdx + 1 until minOf(lines.size, markerIdx + 12))
            .firstOrNull { idx ->
                val n = normalize(lines[idx])
                n == "EISCO" || n == "EISCOLLC"
            } ?: return null

        val shipToCustomer = "EISCO LLC"

        val addressLine1 = lines.getOrNull(shipStart + 1)
            ?.let(::extractEiscoAddressLine)
            ?.takeIf { it.isNotBlank() }

        val lineWithCityStateZip = lines.getOrNull(shipStart + 2)
        val parsed = parseLastCityStateZip(lineWithCityStateZip)

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = null,
            city = parsed.city,
            state = parsed.state,
            zip = parsed.zip
        )
    }

    private fun findShipToClassic(lines: List<String>): ShipToBlock {
        val markerIdx = lines.indexOfFirst {
            val n = normalize(it)
            n.contains("SHIP-TOADDRESS") && n.contains("EISCOLLC")
        }

        val shipToMarkerLine = if (markerIdx >= 0) lines[markerIdx] else null

        val shipToCustomer = shipToMarkerLine?.let {
            extractAfter(it, "ship-to address") ?: extractAfter(it, "ship-toaddress")
        }?.let(::normalizeCustomerName) ?: "EISCO LLC"

        val addressLine1 = if (markerIdx >= 0 && markerIdx + 1 < lines.size) {
            val nextLine = lines[markerIdx + 1]

            when {
                nextLine.contains("475 Quaker Meeting House", ignoreCase = true) ->
                    "475 QUAKER MEETING HOUSE RD"

                else ->
                    extractRightSideAddressLine(nextLine)
                        ?: if (nextLine.contains("QUAKERMEETINGHOUSE", ignoreCase = true)) {
                            "475 QUAKER MEETING HOUSE RD"
                        } else {
                            null
                        }
            }
        } else {
            lines.firstOrNull { normalize(it).contains("QUAKERMEETINGHOUSE") }
                ?.let { "475 QUAKER MEETING HOUSE RD" }
        }

        val cityStateLine = if (markerIdx >= 0 && markerIdx + 2 < lines.size) {
            extractRightSideCityState(lines[markerIdx + 2])
        } else {
            lines.firstOrNull { normalize(it).contains("HONEOYEFALLSNY") }
                ?.let(::extractRightSideCityState)
        }

        val zip = if (markerIdx >= 0 && markerIdx + 3 < lines.size) {
            extractLastZip(lines[markerIdx + 3])
        } else {
            lines.firstOrNull { extractLastZip(it) != null }?.let(::extractLastZip)
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

    private fun extractRightSideAddressLine(line: String): String? {
        if (line.contains("Quaker Meeting House", ignoreCase = true)) {
            return "475 QUAKER MEETING HOUSE RD"
        }

        val matches = Regex(
            """\d+\s+[A-Za-z]+(?:\s+[A-Za-z]+)*\s+(?:Rd|Road)""",
            RegexOption.IGNORE_CASE
        ).findAll(line).map { it.value }.toList()

        return matches.lastOrNull()?.let(::extractEiscoAddressLine)
    }

    private fun extractRightSideCityState(line: String): String? {
        if (line.contains("Honeoye Falls", ignoreCase = true)) {
            return "HONEOYE FALLS NY"
        }

        val matches = Regex(
            """([A-Za-z]+(?:\s+[A-Za-z]+)?),?\s*([A-Z]{2})""",
            RegexOption.IGNORE_CASE
        ).findAll(line).toList()

        val last = matches.lastOrNull() ?: return null
        val city = last.groupValues[1]
            .replace("HoneoyeFalls", "Honeoye Falls", ignoreCase = true)
            .trim()
            .uppercase()
        val state = last.groupValues[2].trim().uppercase()

        return "$city $state"
    }

    private fun findItems(lines: List<String>) =
        buildList {
            var i = 0

            while (i < lines.size) {
                val line = lines[i].replace(Regex("""\s+"""), " ").trim()
                val lineNorm = normalize(line)

                if (isFooterLine(lineNorm) || isHeaderLine(lineNorm)) {
                    i++
                    continue
                }

                val erpInlineMatch = matchEiscoErpInlineItemLine(line)
                if (erpInlineMatch != null) {
                    val leftCode = erpInlineMatch.leftCode
                    val vendorCode = normalizeSkuToken(erpInlineMatch.vendorCode)
                    val quantity = erpInlineMatch.quantity.replace(",", "").toDoubleOrNull()
                    val unitPrice = erpInlineMatch.unitPrice.replace(",", "").toDoubleOrNull()

                    val extraDesc = mutableListOf<String>()
                    var j = i + 1

                    while (j < lines.size) {
                        val next = lines[j].replace(Regex("""\s+"""), " ").trim()
                        val nextNorm = normalize(next)

                        val startsNewErpInline = matchEiscoErpInlineItemLine(next) != null
                        val startsNewClassic = matchEiscoClassicItemLine(next) != null
                        val isFooter = isFooterLine(nextNorm)
                        val isHeader = isHeaderLine(nextNorm)

                        val isContinuation = !startsNewErpInline &&
                                !startsNewClassic &&
                                !isFooter &&
                                !isHeader &&
                                next.isNotBlank()

                        if (!isContinuation) break

                        extraDesc += next
                        j++
                    }

                    val rawDescription = buildString {
                        append(erpInlineMatch.description)
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

                val classicMatch = matchEiscoClassicItemLine(line)
                if (classicMatch != null) {
                    val leftCode = classicMatch.leftCode
                    val firstDesc = classicMatch.description
                    val vendorCode = normalizeSkuToken(classicMatch.vendorCode)
                    val quantity = classicMatch.quantity.replace(",", "").toDoubleOrNull()
                    val unitPrice = classicMatch.unitPrice.replace(",", "").toDoubleOrNull()

                    val extraDesc = mutableListOf<String>()
                    var j = i + 1

                    while (j < lines.size) {
                        val next = lines[j].replace(Regex("""\s+"""), " ").trim()
                        val nextNorm = normalize(next)

                        val startsNewErpInline = matchEiscoErpInlineItemLine(next) != null
                        val startsNewClassic = matchEiscoClassicItemLine(next) != null
                        val isFooter = isFooterLine(nextNorm)
                        val isHeader = isHeaderLine(nextNorm)

                        val isContinuation = !startsNewErpInline &&
                                !startsNewClassic &&
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

    private fun matchEiscoClassicItemLine(line: String): ItemLineMatch? {
        if (matchEiscoErpInlineItemLine(line) != null) return null

        val withComma = Regex(
            """^([A-Z][A-Z0-9]*)\s+(.+?),\s+([A-Z0-9-]+)\s+([\d,]+)\s+\$?(\d+(?:\.\d{1,4})?)\s+\$?([\d,]+\.\d{2})$""",
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
            """^([A-Z][A-Z0-9]*)\s+(.+?)\s+([A-Z0-9-]+)\s+([\d,]+)\s+\$?(\d+(?:\.\d{1,4})?)\s+\$?([\d,]+\.\d{2})$""",
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

    private fun matchEiscoErpInlineItemLine(line: String): ErpInlineItemMatch? {
        val parts = line.split(Regex("""\s+"""))
        if (parts.size < 8) return null

        val lineNumber = parts.firstOrNull() ?: return null
        if (!lineNumber.all { it.isDigit() }) return null

        val leftCode = parts.getOrNull(1) ?: return null
        if (!leftCode.any { it.isLetterOrDigit() }) return null

        val uomValues = setOf("EA", "CS", "PK", "BX")
        val uomIndex = parts.indexOfFirst { it.uppercase() in uomValues }
        if (uomIndex < 4 || uomIndex + 3 >= parts.size) return null

        val vendorCode = parts[uomIndex - 1]
        val quantity = parts[uomIndex + 1]
        val unitPrice = parts[uomIndex + 2]
        val extPrice = parts[uomIndex + 3]

        val quantityOk = Regex("""^[\d,]+$""").matches(quantity)
        val unitPriceOk = Regex("""^\d+(?:\.\d{1,4})?$""").matches(unitPrice)
        val extPriceOk = Regex("""^[\d,]+\.\d{2}$""").matches(extPrice)
        val vendorCodeOk = vendorCode.any { it.isDigit() } || vendorCode.contains("-")

        if (!quantityOk || !unitPriceOk || !extPriceOk || !vendorCodeOk) return null

        val description = parts.subList(2, uomIndex - 1).joinToString(" ").trim()
        if (description.isBlank()) return null

        return ErpInlineItemMatch(
            lineNumber = lineNumber,
            leftCode = leftCode,
            description = description,
            vendorCode = vendorCode,
            uom = parts[uomIndex],
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun cleanVendorDescription(raw: String?, sku: String?, leftCode: String?): String? {
        val merged = raw
            ?.replace("1VIAL/BAG(3X4),", "1 VIAL/BAG (3X4)")
            ?.replace("100STRIPS/VIAL", "100 STRIPS/VIAL")
            ?.replace("Vialof100", "VIAL OF 100")
            ?.replace("TEST-STRIPS100", "TEST STRIPS, 100")
            ?.replace("TESTSTRIP-1BAG", "TEST STRIP - 1 BAG")
            ?.replace("GLUCOSEPLASTIC", "GLUCOSE PLASTIC")
            ?.replace("PLASTICTEST", "PLASTIC TEST")
            ?.replace("ECOPHTESTSTRIP", "ECO PH TEST STRIP")
            ?.replace("STRIP-1BAG", "STRIP - 1 BAG")
            ?.replace("1BAGWITH50", "1 BAG WITH 50")
            ?.replace("7.-10.", "7 - 10")
            ?.replace("100TEST", "100 TEST")
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

            "B0D485KLV6", "BOD485KLV6", "CH202406" ->
                "ECO PH TEST STRIP 7 - 10, 1 BAG WITH 50 STRIPS"

            else -> merged
        }
    }

    private fun extractEiscoAddressLine(line: String): String? {
        return when {
            line.contains("Quaker Meeting House", ignoreCase = true) -> "475 QUAKER MEETING HOUSE RD"
            else -> line
                .replace("475QuakerMeetingHouseRd", "475 Quaker Meeting House Rd")
                .replace("475 Quaker Meeting House Road", "475 QUAKER MEETING HOUSE RD", ignoreCase = true)
                .replace("475 Quaker Meeting House Rd", "475 QUAKER MEETING HOUSE RD", ignoreCase = true)
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { null }
        }
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

    private fun parseLastCityStateZip(line: String?): CityStateZip {
        if (line.isNullOrBlank()) return CityStateZip(null, null, null)

        val cleaned = line
            .replace(",", " ")
            .replace("United States of America", "", ignoreCase = true)
            .replace("United States", "", ignoreCase = true)
            .replace("USA", "", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")
            .trim()

        val matches = Regex(
            """([A-Za-z]+(?:\s+[A-Za-z]+)*)\s+([A-Z]{2})\s+(\d{5})""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()

        val last = matches.lastOrNull()
        return if (last != null) {
            CityStateZip(
                city = last.groupValues[1].trim().uppercase(),
                state = last.groupValues[2].trim().uppercase(),
                zip = last.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, extractLastZip(cleaned))
        }
    }

    private fun normalizeCustomerName(value: String): String {
        val compact = value
            .replace(Regex("""\s+"""), " ")
            .trim()

        return when {
            compact.equals("EISCOLLC", ignoreCase = true) -> "EISCO LLC"
            compact.equals("EISCOLLCLLC", ignoreCase = true) -> "EISCO LLC"
            compact.equals("EISCO", ignoreCase = true) -> "EISCO LLC"
            compact.equals("EISCO LLC", ignoreCase = true) -> "EISCO LLC"
            compact.equals("Eisco LLC", ignoreCase = true) -> "EISCO LLC"
            else -> compact.uppercase()
        }
    }

    private fun normalizeSkuToken(value: String): String {
        val trimmed = value.trim().uppercase()
        return when (trimmed) {
            "BOD485KLV6" -> "B0D485KLV6"
            else -> trimmed
        }
    }

    private fun isFooterLine(normalized: String): Boolean {
        return normalized.contains("SUB-TOTAL") ||
                normalized.contains("TOTAL$") ||
                normalized.contains("POTOTAL") ||
                normalized.contains("TAXTOTAL") ||
                normalized.contains("TOTAL(USD)") ||
                normalized.startsWith("PAGE")
    }

    private fun isHeaderLine(normalized: String): Boolean {
        return normalized.contains("PRODUCTDESCRIPTION") ||
                normalized == "CODE" ||
                normalized.contains("LNBRITEMDESCVENDORIDUOMQTYUNITPRICEEXTPRICE")
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

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class ItemLineMatch(
        val leftCode: String,
        val description: String,
        val vendorCode: String,
        val quantity: String,
        val unitPrice: String
    )

    private data class ErpInlineItemMatch(
        val lineNumber: String,
        val leftCode: String,
        val description: String,
        val vendorCode: String,
        val uom: String,
        val quantity: String,
        val unitPrice: String
    )
}