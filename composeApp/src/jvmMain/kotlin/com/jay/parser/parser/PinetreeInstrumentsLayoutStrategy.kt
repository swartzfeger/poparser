package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class PinetreeInstrumentsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "PINETREE_INSTRUMENTS"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("PINETREEINSTRUMENTS") &&
                text.contains("PURCHASEORDER#") &&
                text.contains("VENDORPRODCODE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("PINETREEINSTRUMENTSINC")) score += 100
        if (text.contains("PINETREEINDIGOINSTRUMENTSINC")) score += 100
        if (text.contains("PURCHASEORDER#")) score += 80
        if (text.contains("VENDORPRODCODE")) score += 70
        if (text.contains("169LEXINGTONCOURT")) score += 40
        if (text.contains("WATERLOOONN2J4R9")) score += 40
        if (text.contains("33815-1000")) score += 20
        if (text.contains("33819-1500")) score += 20
        if (text.contains("33810-B8")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)
        val customerName = "PINETREE INSTRUMENTS"
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
            terms = mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = normalizeLine(lines[i])

            val inline = Regex(
                """Purchase\s*Order\s*#\s*(\d{5,})""",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (inline != null) return inline.groupValues[1].trim()

            if (line.equals("Purchase Order #", ignoreCase = true)) {
                for (j in (i + 1)..minOf(i + 3, lines.lastIndex)) {
                    val candidate = normalizeLine(lines[j])
                    if (candidate.matches(Regex("""\d{5,}"""))) {
                        return candidate
                    }
                }
            }
        }

        return lines.firstOrNull { normalizeLine(it).matches(Regex("""\d{5,}""")) }
            ?.let { normalizeLine(it) }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val anchorIndex = lines.indexOfFirst {
            val compactLine = compact(it)
            compactLine.contains("SHIPTO:") || compactLine.contains("DROPSHIPTO:")
        }

        if (anchorIndex < 0) {
            return ShipToBlock(null, null, null, null, null, null)
        }

        val rawBlock = mutableListOf<String>()
        for (i in (anchorIndex + 1)..lines.lastIndex) {
            val line = normalizeLine(lines[i])
            if (line.isBlank()) continue

            if (line.contains("P/O Date", ignoreCase = true) ||
                line.contains("Vendor Prod Code", ignoreCase = true) ||
                line.contains("Product Code", ignoreCase = true)
            ) {
                break
            }

            rawBlock.add(line)
        }

        val rightOnly = rawBlock
            .map { stripKnownVendorLeftColumn(it) }
            .map { normalizeLine(it) }
            .filter { it.isNotBlank() }
            .filterNot { it.equals("Canada", ignoreCase = true) }
            .filterNot { it.equals("US", ignoreCase = true) }
            .filterNot { it.startsWith("Phone:", ignoreCase = true) }
            .filterNot { it.startsWith("Fax:", ignoreCase = true) }

        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val usable = rightOnly.filterNot { it.startsWith("Attn:", ignoreCase = true) }

        for (line in usable) {
            if (shipToCustomer == null &&
                !looksLikeUsCityStateZip(line) &&
                !looksLikeCanadianCityPostal(line) &&
                !looksLikeStreetAddress(line)
            ) {
                shipToCustomer = normalizeHumanText(line.trim('(', ')').trim())
                continue
            }

            if (addressLine1 == null && looksLikeStreetAddress(line)) {
                addressLine1 = normalizeHumanText(line)
                continue
            }

            if (line.startsWith("(") && line.endsWith(")") && shipToCustomer != null) {
                val extra = normalizeHumanText(line.trim('(', ')').trim())
                shipToCustomer = "$shipToCustomer ($extra)"
                continue
            }

            val us = Regex(
                """^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (us != null) {
                city = normalizeHumanText(us.groupValues[1].trim())
                state = us.groupValues[2].trim().uppercase()
                zip = us.groupValues[3].trim()
                continue
            }

            val ca = Regex(
                """^(.+?),\s*([A-Z]{2})\s+([A-Z]\d[A-Z]\s?\d[A-Z]\d)(?:,\s*CANADA)?$""",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (ca != null) {
                city = normalizeHumanText(ca.groupValues[1].trim())
                state = ca.groupValues[2].trim().uppercase()
                zip = ca.groupValues[3]
                    .trim()
                    .uppercase()
                    .replace(Regex("""\s+"""), "")
                    .replace(Regex("""^([A-Z]\d[A-Z])(\d[A-Z]\d)$"""), "$1 $2")
                continue
            }

            if (addressLine1 != null && addressLine2 == null && !looksLikeStreetAddress(line)) {
                addressLine2 = normalizeHumanText(line)
            }
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        val start = lines.indexOfFirst {
            val c = compact(it)
            c.contains("PRODUCTCODEPRODUCTNAME") || c.contains("VENDORPRODCODE")
        }
        if (start < 0) return emptyList()

        var i = start + 1
        while (i < lines.size) {
            val line = normalizeLine(lines[i])

            if (isTerminalLine(line)) break

            if (!startsNewPinetreeItem(line)) {
                i++
                continue
            }

            val block = mutableListOf(line)
            i++

            while (i < lines.size) {
                val next = normalizeLine(lines[i])

                if (next.isBlank()) {
                    i++
                    continue
                }

                if (startsNewPinetreeItem(next) || isTerminalLine(next)) {
                    break
                }

                block.add(next)
                i++
            }

            val item = parseItemBlock(block) ?: continue
            val key = "${item.sku}|${item.quantity}|${item.unitPrice}"
            if (!seen.add(key)) continue

            items.add(item)
        }

        return items
    }

    private fun parseItemBlock(block: List<String>): ParsedPdfItem? {
        val rawLines = block.map { normalizeLine(it) }.filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return null

        val first = rawLines.first()

        var productCode = ""
        var descriptionPortion = ""
        var quantity = 0.0
        var unitPrice = 0.0
        val vendorParts = mutableListOf<String>()
        val descriptionExtra = mutableListOf<String>()

        val patternA = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+(\d+)\s+(\d+(?:\.\d{1,2})?)\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        val patternB = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+([A-Z0-9-]+)\s+(\d+)\s+(\d+(?:\.\d{1,2})?)\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        val patternC = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+(\d+)\s+(\d+\.\d{1,2})\s+([A-Z0-9-]+)\s+EACH\s+(\d{1,2})\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        val patternD = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+([A-Z0-9-]+)\s+(\d+)EACH\s+(\d+(?:\.\d{1,2})?)\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        val patternE = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+(\d+)EACH\s+(\d+(?:\.\d{1,2})?)\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        val patternF = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*)\s+(.+?)\s+([A-Z0-9-]+)\s+(\d+)Lotof\d+\s+(\d+(?:\.\d{1,2})?)\s+0\.00%\s+\d[\d,]*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).find(first)

        when {
            patternF != null -> {
                productCode = patternF.groupValues[1].trim().uppercase()
                descriptionPortion = patternF.groupValues[2].trim()
                vendorParts.add(patternF.groupValues[3].trim().uppercase())
                quantity = patternF.groupValues[4].toDouble()
                unitPrice = patternF.groupValues[5].toDouble()
            }

            patternD != null -> {
                productCode = patternD.groupValues[1].trim().uppercase()
                descriptionPortion = patternD.groupValues[2].trim()
                vendorParts.add(patternD.groupValues[3].trim().uppercase())
                quantity = patternD.groupValues[4].toDouble()
                unitPrice = patternD.groupValues[5].toDouble()
            }

            patternE != null -> {
                productCode = patternE.groupValues[1].trim().uppercase()
                descriptionPortion = patternE.groupValues[2].trim()
                quantity = patternE.groupValues[3].toDouble()
                unitPrice = patternE.groupValues[4].toDouble()
            }

            patternC != null -> {
                productCode = patternC.groupValues[1].trim().uppercase()
                descriptionPortion = patternC.groupValues[2].trim()
                quantity = patternC.groupValues[3].toDouble()
                unitPrice = "${patternC.groupValues[4]}${patternC.groupValues[6]}".toDouble()
                vendorParts.add(patternC.groupValues[5].trim().uppercase())
            }

            patternB != null -> {
                productCode = patternB.groupValues[1].trim().uppercase()
                descriptionPortion = patternB.groupValues[2].trim()
                vendorParts.add(patternB.groupValues[3].trim().uppercase())
                quantity = patternB.groupValues[4].toDouble()
                unitPrice = patternB.groupValues[5].toDouble()
            }

            patternA != null -> {
                productCode = patternA.groupValues[1].trim().uppercase()
                descriptionPortion = patternA.groupValues[2].trim()
                quantity = patternA.groupValues[3].toDouble()
                unitPrice = patternA.groupValues[4].toDouble()
            }

            else -> return null
        }

        val trailingVendor = extractTrailingVendorFragment(descriptionPortion)
        if (trailingVendor != null) {
            vendorParts.add(trailingVendor)
            val idx = descriptionPortion.lastIndexOf(trailingVendor, ignoreCase = true)
            if (idx >= 0) {
                descriptionPortion = descriptionPortion.substring(0, idx).trim().trimEnd(',', ';')
            }
        }

        val baseDescription = normalizeHumanText(descriptionPortion)

        for (line in rawLines.drop(1)) {
            val cleaned = line.trim()
            if (cleaned.isBlank()) continue
            if (cleaned.startsWith("Notes ", ignoreCase = true)) continue

            val noEach = cleaned.replace(Regex("""\s*EACH$""", RegexOption.IGNORE_CASE), "").trim()

            when {
                noEach.equals("100", ignoreCase = true) -> vendorParts.add("100")
                noEach.equals("50", ignoreCase = true) -> vendorParts.add("50")
                noEach.equals("100 Indigo", ignoreCase = true) -> vendorParts.add("100")
                noEach.equals("100Indigo", ignoreCase = true) -> vendorParts.add("100")
                noEach.equals("50 Indigo", ignoreCase = true) -> vendorParts.add("50")
                noEach.equals("50Indigo", ignoreCase = true) -> vendorParts.add("50")

                noEach.matches(Regex("""^1V-(50|100)(?:INDIGO)?$""", RegexOption.IGNORE_CASE)) -> {
                    vendorParts.add(
                        noEach.uppercase()
                            .replace("INDIGO", "")
                            .replace(" ", "")
                    )
                }

                noEach.matches(Regex("""^[A-Z0-9-]+$""", RegexOption.IGNORE_CASE)) &&
                        (noEach.startsWith("PIN-") ||
                                noEach.startsWith("QAC-") ||
                                noEach.startsWith("PER-") ||
                                noEach.startsWith("CHL") ||
                                noEach == "M-NUT-MAC" ||
                                noEach.matches(Regex("""^\d+-\d+-[A-Z0-9xX-]+$"""))) -> {
                    vendorParts.add(noEach.uppercase())
                }

                noEach.matches(Regex("""^[A-Z0-9-]+\s+LOTOF\d+$""", RegexOption.IGNORE_CASE)) -> {
                    val firstPart = noEach.substringBefore(' ').uppercase()
                    vendorParts.add(firstPart)
                }

                else -> descriptionExtra.add(normalizeHumanText(cleaned))
            }
        }

        val vialSuffixFromBlock = rawLines
            .drop(1)
            .map { normalizeLine(it).uppercase() }
            .map { it.replace("INDIGO", "").replace(" ", "") }
            .firstOrNull { it.matches(Regex("""^1V-(50|100)$""")) }

        val joinedVendor = joinVendorSku(vendorParts)

        val repairedVendor = when {
            joinedVendor.endsWith("-") && vialSuffixFromBlock != null -> joinedVendor + vialSuffixFromBlock
            else -> joinedVendor
        }

        var sku = normalizePinetreeSku(repairedVendor)
        if (sku.isBlank()) sku = normalizePinetreeSku(productCode)

        val finalDescription = ItemMapper.getItemDescription(sku).ifBlank {
            normalizeHumanText(
                (listOf(baseDescription) + descriptionExtra)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .normalizeSpaces()
            )
        }

        val mappedUnitPrice = ItemMapper.getItemPrice(sku, "DIST - 10%")
        val finalUnitPrice = if (mappedUnitPrice > 0.0) mappedUnitPrice else unitPrice

        return item(
            sku = sku,
            description = finalDescription,
            quantity = quantity,
            unitPrice = finalUnitPrice
        )
    }

    private fun extractTrailingVendorFragment(text: String): String? {
        val patterns = listOf(
            Regex("""(PIN-MNTX-)$""", RegexOption.IGNORE_CASE),
            Regex("""(PIN-QA15-)$""", RegexOption.IGNORE_CASE),
            Regex("""(QAC-[A-Z0-9-]*-)$""", RegexOption.IGNORE_CASE),
            Regex("""(PER-[A-Z0-9-]*-)$""", RegexOption.IGNORE_CASE),
            Regex("""(CHL-?[A-Z0-9-]*-)$""", RegexOption.IGNORE_CASE),
            Regex("""(PH[A-Z0-9-]*-)$""", RegexOption.IGNORE_CASE),
            Regex("""(M-NUT-MAC)$""", RegexOption.IGNORE_CASE),
            Regex("""(\d+-\d+-[A-Z0-9xX-]+(?:\s+Lotof\d+)?)$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim().uppercase()
                    .replace(Regex("""^MAINTEX""", RegexOption.IGNORE_CASE), "")
            }
        }

        return null
    }

    private fun normalizePinetreeSku(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var sku = input.uppercase()
            .replace("INDIGO", "")
            .replace(" ", "")
            .replace("_", "-")
            .trim()

        sku = sku.replace(Regex("""CHL2000-""", RegexOption.IGNORE_CASE), "CHL-2000-")
        sku = sku.replace(Regex("""CHL300-""", RegexOption.IGNORE_CASE), "CHL-300-")
        sku = sku.replace(Regex("""^280-25-8X10$""", RegexOption.IGNORE_CASE), "280-25-810")

        if (sku == "PIN-MNTX-") sku = "PIN-MNTX-100"
        if (sku == "PIN-QA15-") sku = "PIN-QA15-100"
        if (sku == "QAC-100-1V-") sku = "QAC-100-1V-100"
        if (sku == "CHL-2000-1V-") sku = "CHL-2000-1V-100"

        sku = sku.replace(Regex("""LOTOF\d+$""", RegexOption.IGNORE_CASE), "")
        sku = sku.trim()

        return sku
    }

    private fun joinVendorSku(parts: List<String>): String {
        if (parts.isEmpty()) return ""

        var joined = parts.joinToString(" ").normalizeSpaces()

        joined = joined.replace("100 Indigo", "100", ignoreCase = true)
        joined = joined.replace("100Indigo", "100", ignoreCase = true)
        joined = joined.replace("50 Indigo", "50", ignoreCase = true)
        joined = joined.replace("50Indigo", "50", ignoreCase = true)

        joined = joined.replace(Regex("""-\s+(\d+)"""), "-$1")
        joined = joined.replace(Regex("""\s+(1V-\d+)""", RegexOption.IGNORE_CASE), "$1")
        joined = joined.replace(Regex("""\s+Lotof""", RegexOption.IGNORE_CASE), " Lotof")
        joined = joined.replace(Regex("""\s*INDIGO$""", RegexOption.IGNORE_CASE), "")
        joined = joined.replace(Regex("""\s+"""), " ").trim()

        return joined
    }

    private fun stripKnownVendorLeftColumn(line: String): String {
        var s = normalizeLine(line)

        val patterns = listOf(
            Regex("""^Precision Laboratories\s+""", RegexOption.IGNORE_CASE),
            Regex("""^415 Airpark Rd\.?\s+""", RegexOption.IGNORE_CASE),
            Regex("""^Cottonwood,\s*AZ\s+86326\s+""", RegexOption.IGNORE_CASE),
            Regex("""^Fax:\s*\(928\)\s*649-2306\s*""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            s = s.replace(pattern, "").trim()
        }

        return s
    }

    private fun startsNewPinetreeItem(line: String): Boolean {
        val s = normalizeLine(line)

        if (s.startsWith("Notes ", ignoreCase = true)) return false
        if (s.startsWith("Sub Total", ignoreCase = true)) return false
        if (s.startsWith("Total:", ignoreCase = true)) return false
        if (s.startsWith("Printed:", ignoreCase = true)) return false
        if (s.startsWith("GST", ignoreCase = true)) return false
        if (s.startsWith("PST", ignoreCase = true)) return false

        return Regex(
            """^[A-Z0-9]+(?:-[A-Z0-9]+)*\s+.+0\.00%\s+\d+(?:,\d{3})*(?:\.\d{2})$""",
            RegexOption.IGNORE_CASE
        ).matches(s)
    }

    private fun isTerminalLine(line: String): Boolean {
        return line.startsWith("Sub Total", ignoreCase = true) ||
                line.startsWith("Total:", ignoreCase = true) ||
                line.startsWith("Printed:", ignoreCase = true) ||
                line.startsWith("GST", ignoreCase = true) ||
                line.startsWith("PST", ignoreCase = true)
    }

    private fun looksLikeVendorCodeLine(line: String): Boolean {
        val s = normalizeLine(line)

        if (s.equals("100", ignoreCase = true)) return true
        if (s.equals("50", ignoreCase = true)) return true
        if (s.equals("100 Indigo", ignoreCase = true)) return true
        if (s.equals("100Indigo", ignoreCase = true)) return true
        if (s.equals("50 Indigo", ignoreCase = true)) return true
        if (s.equals("50Indigo", ignoreCase = true)) return true
        if (s.matches(Regex("""^1V-(50|100)(?:INDIGO)?$""", RegexOption.IGNORE_CASE))) return true

        return Regex(
            """^(PIN-[A-Z0-9-]+|QAC-[A-Z0-9-]+|PER-[A-Z0-9-]+|CHL-?[A-Z0-9-]+|PH[A-Z0-9-]+|M-NUT-MAC|\d+-\d+-[A-Z0-9xX-]+(?:\s+Lotof\d+)?)$""",
            RegexOption.IGNORE_CASE
        ).matches(s)
    }

    private fun looksLikeStreetAddress(line: String): Boolean {
        val s = normalizeLine(line)
        return Regex("""^\d+\s+.+""").matches(s) ||
                s.contains("Route", ignoreCase = true) ||
                s.contains("Stafford", ignoreCase = true) ||
                s.contains("Lexington", ignoreCase = true) ||
                s.contains("Building", ignoreCase = true)
    }

    private fun looksLikeUsCityStateZip(line: String): Boolean {
        return Regex(
            """^.+?,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?$""",
            RegexOption.IGNORE_CASE
        ).matches(normalizeLine(line))
    }

    private fun looksLikeCanadianCityPostal(line: String): Boolean {
        return Regex(
            """^.+?,\s*[A-Z]{2}\s+[A-Z]\d[A-Z]\s?\d[A-Z]\d(?:,\s*CANADA)?$""",
            RegexOption.IGNORE_CASE
        ).matches(normalizeLine(line))
    }

    private fun normalizeHumanText(value: String): String {
        var s = value.trim()
        s = s.replace(Regex("""\bCityof\b""", RegexOption.IGNORE_CASE), "City of")
        s = s.replace(Regex("""\bCranberryTownship\b""", RegexOption.IGNORE_CASE), "Cranberry Township")
        s = s.replace(Regex("""\bMaintexInc\.?\b""", RegexOption.IGNORE_CASE), "Maintex Inc.")
        s = s.replace(Regex("""\bAsdiscussedwith\b""", RegexOption.IGNORE_CASE), "As discussed with")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }

    private fun normalizeLine(value: String): String {
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    private fun String.normalizeSpaces(): String {
        return this.replace(Regex("""\s+"""), " ").trim()
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9:#]"""), "")
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}