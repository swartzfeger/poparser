package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import kotlin.math.round

class TsaInvoiceLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "TSA_INVOICE"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("INVOICE") &&
                text.contains("PRECISIONLABORATORIES") &&
                text.contains("SKU:TSAPER100") &&
                (
                        text.contains("@TSA.DHS.GOV") ||
                                text.contains("TSAPER100") && text.contains("LIQUIDSAMPLETESTSTRIPS")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0

        if (text.contains("INVOICE")) score += 50
        if (text.contains("PRECISIONLABORATORIES")) score += 40
        if (text.contains("SKU:TSAPER100")) score += 120
        if (text.contains("LIQUIDSAMPLETESTSTRIPS")) score += 80
        if (text.contains("@TSA.DHS.GOV")) score += 100
        if (text.contains("SHIPTO:")) score += 40
        if (text.contains("ORDERNUMBER:SO-W")) score += 60
        if (text.contains("INVOICENUMBER:SO-W")) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { normalizeLine(it) }
        val shipTo = parseShipTo(clean)
        val accountLocation = parseAccountLocation(clean) ?: ParsedCityStateZip(
            city = shipTo.city.orEmpty(),
            state = shipTo.state.orEmpty(),
            zip = shipTo.zip.orEmpty(),
            sourceLine = "",
            sourceIndex = -1
        )

        // TSA invoices can have one billing/location block and a different Ship To block.
        // Sage customer resolution must use the location portion of the Customer ID, which
        // corresponds to the non-Ship To/customer block, not necessarily the physical ship-to city.
        val tsaAccount = TsaLocationMapper.resolveByAccountLocation(
            city = accountLocation.city,
            state = accountLocation.state,
            zip = accountLocation.zip
        )

        return ParsedPdfFields(
            customerName = tsaAccount?.customerId ?: "TSA-UNRESOLVED",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = tsaAccount?.terms ?: "Prepaid",
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        Regex("""Order\s*Number:\s*(SO-W\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it.trim() }

        Regex("""Invoice\s*Number:\s*(SO-W\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it.trim() }

        Regex("""\bSO-W\d+\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.let { return it.trim().uppercase() }

        return null
    }

    private fun parseAccountLocation(lines: List<String>): ParsedCityStateZip? {
        val productIndex = lines.indexOfFirst {
            compact(it).contains("PRODUCTQUANTITYPRICE") ||
                    compact(it).contains("LIQUIDSAMPLETESTSTRIPS")
        }.let { if (it >= 0) it else lines.size }

        val invoiceIndex = lines.indexOfFirst { compact(it).contains("INVOICE") }
            .let { if (it >= 0) it else 0 }

        val window = lines.subList(invoiceIndex, productIndex).filter { it.isNotBlank() }
        return findAccountCityStateZip(window)
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val productIndex = lines.indexOfFirst {
            compact(it).contains("PRODUCTQUANTITYPRICE") ||
                    compact(it).contains("LIQUIDSAMPLETESTSTRIPS")
        }.let { if (it >= 0) it else lines.size }

        val invoiceIndex = lines.indexOfFirst { compact(it).contains("INVOICE") }
            .let { if (it >= 0) it else 0 }

        val window = lines.subList(invoiceIndex, productIndex).filter { it.isNotBlank() }

        val csz = findShipToCityStateZip(window)
        val shipToName = parseShipToName(window)
        val addressLine1 = parseShipToAddressLine1(window, csz)
        val addressLine2 = parseShipToAddressLine2(window, csz, addressLine1)

        return ShipToBlock(
            shipToCustomer = shipToName,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = csz?.city,
            state = csz?.state,
            zip = csz?.zip
        )
    }

    private fun findShipToCityStateZip(lines: List<String>): ParsedCityStateZip? {
        var last: ParsedCityStateZip? = null

        val pattern = Regex(
            """([A-Za-z][A-Za-z .'-]+),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)""",
            RegexOption.IGNORE_CASE
        )

        for ((index, line) in lines.withIndex()) {
            val normalizedLine = normalizeHumanText(line)
            val matches = pattern.findAll(normalizedLine).toList()
            if (matches.isEmpty()) continue

            val chosen = matches.last()
            last = ParsedCityStateZip(
                city = normalizeHumanText(chosen.groupValues[1]),
                state = chosen.groupValues[2].uppercase(),
                zip = chosen.groupValues[3],
                sourceLine = line,
                sourceIndex = index
            )
        }

        return last
    }

    private fun findAccountCityStateZip(lines: List<String>): ParsedCityStateZip? {
        val pattern = Regex(
            """([A-Za-z][A-Za-z .'-]+),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)""",
            RegexOption.IGNORE_CASE
        )

        for ((index, line) in lines.withIndex()) {
            val normalizedLine = normalizeHumanText(line)
            val first = pattern.findAll(normalizedLine).firstOrNull() ?: continue
            return ParsedCityStateZip(
                city = normalizeHumanText(first.groupValues[1]),
                state = first.groupValues[2].uppercase(),
                zip = first.groupValues[3],
                sourceLine = line,
                sourceIndex = index
            )
        }

        return null
    }

    private fun parseShipToName(lines: List<String>): String? {
        val shipToLineIndex = lines.indexOfFirst { compact(it).contains("SHIPTO:") }
        if (shipToLineIndex < 0) return null

        for (i in (shipToLineIndex + 1)..minOf(shipToLineIndex + 3, lines.lastIndex)) {
            val raw = lines[i]
                .replace(Regex("""Invoice\s*Date:.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Order\s*Number:.*$""", RegexOption.IGNORE_CASE), "")
                .trim()

            val withoutLeadingCode = raw
                .replace(Regex("""^(?:[A-Z]{2,4}|TSA-[A-Z0-9-]+)\s+"""), "")
                .trim()

            if (withoutLeadingCode.isNotBlank() &&
                !looksLikeStreetAddress(withoutLeadingCode) &&
                !looksLikeCityStateZip(withoutLeadingCode)
            ) {
                return normalizeHumanText(withoutLeadingCode)
            }
        }

        return null
    }

    private fun parseShipToAddressLine1(lines: List<String>, csz: ParsedCityStateZip?): String? {
        val searchLines = if (csz != null) {
            lines.take(csz.sourceIndex + 1)
        } else {
            lines
        }

        val candidates = searchLines.flatMap { extractStreetCandidates(it) }
        return candidates.lastOrNull()
    }

    private fun parseShipToAddressLine2(
        lines: List<String>,
        csz: ParsedCityStateZip?,
        addressLine1: String?
    ): String? {
        // First prefer any Suite/Ste/Floor/Dock text that trails the chosen city/state/zip.
        csz?.let {
            trailingAfterLastCityStateZip(it.sourceLine)?.let { suffix ->
                cleanAddressLine2(suffix)?.let { cleaned -> return cleaned }
            }
        }

        // Then scan other two-column city/state/zip rows. This catches rows where
        // the bill-to city appears first and the ship-to suite appears as trailing text.
        for (line in lines.asReversed()) {
            trailingAfterLastCityStateZip(line)?.let { suffix ->
                cleanAddressLine2(suffix)?.let { cleaned -> return cleaned }
            }
        }

        // Then look immediately after the selected street address on combined rows.
        if (!addressLine1.isNullOrBlank()) {
            val compactAddress = compact(addressLine1)
            for (line in lines.asReversed()) {
                val normalized = normalizeHumanText(line)
                val candidates = extractStreetCandidatesWithRanges(normalized)
                val match = candidates.lastOrNull { compact(it.value) == compactAddress } ?: continue
                val suffix = normalized.substring(match.endIndex).trim()
                cleanAddressLine2(suffix)?.let { return it }
            }
        }

        return null
    }

    private fun trailingAfterLastCityStateZip(line: String): String? {
        val normalized = normalizeHumanText(line)
        val pattern = Regex(
            """[A-Za-z][A-Za-z .'-]+,\s*[A-Z]{2}\s*\d{5}(?:-\d{4})?""",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.findAll(normalized).lastOrNull() ?: return null
        return normalized.substring(match.range.last + 1).trim()
    }

    private fun extractStreetCandidates(line: String): List<String> {
        return extractStreetCandidatesWithRanges(normalizeHumanText(line)).map { it.value }
    }

    private fun extractStreetCandidatesWithRanges(line: String): List<TextMatch> {
        val normalized = normalizeHumanText(line)
        val suffixPattern =
            """(?:Rd|Road|Ave|Avenue|Blvd|Boulevard|Dr|Drive|Parkway|Pkwy|Way|Court|Ct)\.?""" +
                    """(?:\s+(?:N|S|E|W|North|South|East|West|NE|NW|SE|SW))?"""
        val streetStartRegex = Regex("""\b\d{1,6}\b""")
        val streetFromStartRegex = Regex(
            """^\d{1,6}\s+(?:[A-Za-z0-9'.#-]+\s+){1,9}$suffixPattern\b""",
            RegexOption.IGNORE_CASE
        )

        val matches = mutableListOf<TextMatch>()
        for (numberMatch in streetStartRegex.findAll(normalized)) {
            val start = numberMatch.range.first
            val fragment = normalized.substring(start)

            // Ignore floor markers such as "3rd Floor" when they appear before
            // the actual ship-to street address on the same OCR row.
            if (Regex("""^\d{1,2}\s*(?:st|nd|rd|th)\s+Floor\b""", RegexOption.IGNORE_CASE).containsMatchIn(fragment)) {
                continue
            }

            // Ignore suite/unit numbers when the real street number follows later
            // on the same OCR row, e.g. "Suite 200 17801 International Blvd. South".
            val prefix = normalized.substring(0, start).trim()
            if (Regex("""(?:Suite|Ste\.?)$""", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
                continue
            }

            val streetMatch = streetFromStartRegex.find(fragment) ?: continue
            val value = normalizeHumanText(streetMatch.value)
            matches.add(TextMatch(value = value, startIndex = start, endIndex = start + streetMatch.range.last + 1))
        }

        return matches.distinctBy { compact(it.value) }
    }

    private fun cleanAddressLine2(value: String?): String? {
        if (value.isNullOrBlank()) return null

        var cleaned = normalizeHumanText(value)
            .replace(Regex("""^United\s*States.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\(US\).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Payment\s*Method:.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Invoice\s*Date:.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Order\s*Date:.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Order\s*Number:.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Shipping\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d{3}[- ]?\d{3}[- ]?\d{4}\b"""), "")
            .trim()

        cleaned = cleaned
            .replace(Regex("""\bSuite\s*(\d+[A-Za-z]?)\b""", RegexOption.IGNORE_CASE), "Suite $1")
            .replace(Regex("""\bSte\.?\s*([A-Za-z0-9]+)\b""", RegexOption.IGNORE_CASE), "Ste. $1")
            .replace(Regex("""\bDock\s*(\d+[A-Za-z]?)\b""", RegexOption.IGNORE_CASE), "Dock $1")
            .replace(Regex("""\bTSA\s*DHS\b""", RegexOption.IGNORE_CASE), "TSA DHS")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank()) return null
        if (cleaned.equals("United States", ignoreCase = true)) return null
        if (cleaned.equals("US", ignoreCase = true)) return null
        if (isIgnorableTsaAddressLine(cleaned)) return null
        if (looksLikeStreetAddress(cleaned)) return null
        if (looksLikeCityStateZip(cleaned)) return null

        val allowed = Regex(
            """\b(Suite|Ste\.?|Dock|Floor|Fl\.?)\b""",
            RegexOption.IGNORE_CASE
        )
        if (!allowed.containsMatchIn(cleaned)) return null

        return cleaned
    }


    private fun isIgnorableTsaAddressLine(value: String): Boolean {
        val normalized = value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
        return normalized == "TSADHS" ||
                normalized == "TSADEPARTMENTOFHOMELANDSECURITY" ||
                normalized == "DEPARTMENTOFHOMELANDSECURITY" ||
                normalized == "UNITEDSTATESUS" ||
                normalized == "UNITEDSTATES" ||
                normalized == "US"
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val joined = lines.joinToString(" ")

        val sku = Regex("""SKU:\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
            ?: "TSAPER100"

        val itemLine = lines.firstOrNull {
            compact(it).contains("LIQUIDSAMPLETESTSTRIPS") &&
                    Regex("""\b\d+\s+\$[\d,]+\.\d{2}""").containsMatchIn(it)
        }

        val match = itemLine?.let {
            Regex("""Liquid\s*Sample\s*Test\s*Strips\s+(\d+)\s+\$([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
                .find(it)
        }

        val quantity = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val lineTotal = match?.groupValues?.getOrNull(2)?.replace(",", "")?.toDoubleOrNull()
        val unitPrice = if (quantity != null && quantity > 0.0 && lineTotal != null) {
            roundToCents(lineTotal / quantity)
        } else {
            null
        }

        if (quantity == null || unitPrice == null) return emptyList()

        return listOf(
            ParsedPdfItem(
                sku = sku,
                description = "Liquid Sample Test Strips",
                quantity = quantity,
                unitPrice = unitPrice
            )
        )
    }

    private fun roundToCents(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private fun normalizeLine(value: String): String {
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9@:#.-]+"""), "")
    }

    private fun normalizeHumanText(value: String): String {
        val withSpaces = value
            .replace(Regex("""\bCTRofSVCTNNL\b""", RegexOption.IGNORE_CASE), "CTR of SVC TNNL")
            .replace(Regex("""\bCTR\s*of\s*SVC\s*TNNL\b""", RegexOption.IGNORE_CASE), "CTR of SVC TNNL")
            .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
            .replace(Regex("""([A-Z])([A-Z][a-z])"""), "$1 $2")
            .replace(Regex("""([A-Za-z])([0-9])"""), "$1 $2")
            .replace(Regex("""([0-9])([A-Za-z])"""), "$1 $2")
            .replace(Regex("""\bTSA\s*DHS\b""", RegexOption.IGNORE_CASE), "TSA DHS")
            .replace(".", ". ")
            .replace(Regex(""",(?=\S)"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return withSpaces
            .replace(Regex("""\bSte\.?\s*""", RegexOption.IGNORE_CASE), "Ste. ")
            .replace(Regex("""\bSuite\s*""", RegexOption.IGNORE_CASE), "Suite ")
            .replace(Regex("""\bDock\s*""", RegexOption.IGNORE_CASE), "Dock ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun looksLikeStreetAddress(value: String): Boolean {
        return extractStreetCandidates(value).isNotEmpty()
    }

    private fun looksLikeCityStateZip(value: String): Boolean {
        return Regex(
            """[A-Za-z][A-Za-z .'-]+,\s*[A-Z]{2}\s*\d{5}(?:-\d{4})?""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(normalizeHumanText(value))
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class ParsedCityStateZip(
        val city: String,
        val state: String,
        val zip: String,
        val sourceLine: String,
        val sourceIndex: Int
    )

    private data class TextMatch(
        val value: String,
        val startIndex: Int,
        val endIndex: Int
    )
}
