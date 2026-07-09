package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import kotlin.math.abs
import kotlin.math.roundToInt

class FisherScientificCoLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Fisher Scientific Co"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString(" ")
            .replace("|", " ")
            .uppercase()

        val hasPrecisionVendor =
            text.contains("PRECISION") ||
                    text.contains("PRECISTON") ||
                    text.contains("PREC ISITON") ||
                    text.contains("LABORATORIES")

        val hasFisherOrderTable =
            text.contains("FISHER") &&
                    text.contains("SUPPLIER") &&
                    text.contains("CATALOG") &&
                    text.contains("TOTAL")

        return text.contains("FISHER") && (hasPrecisionVendor || hasFisherOrderTable)
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("FISHER")) score += 100
        if (
            text.contains("PRECISION") ||
            text.contains("PRECISTON") ||
            text.contains("PREC ISITON") ||
            text.contains("LABORATORIES")
        ) score += 80
        if (text.contains("ORDER NOTES AND INSTRUCTIONS")) score += 60
        if (text.contains("CATALOG")) score += 40
        if (
            text.contains("FISHER PO LINE") ||
            text.contains("FISHER FO LINE") ||
            text.contains("PISHER FPO LINE")
        ) score += 60

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val fullText = textLines.joinToString("\n").uppercase()

        val shipToBlock = extractShipToBlock(textLines)
        val primaryAddressData = resolveAddressFromShipToBlock(shipToBlock)
            ?: resolveAddressFromStreet(shipToBlock ?: fullText)
            ?: emptyMap()

        val addressData = if (primaryAddressData["addressLine1"].isNullOrBlank()) {
            resolveAddressFromStreet(fullText) ?: primaryAddressData
        } else {
            primaryAddressData
        }

        return ParsedPdfFields(
            customerName = "FISHER SCIENTIFIC CO",
            orderNumber = findOrderNumber(fullText, textLines),
            shipToCustomer = "FISHER SCIENTIFIC COMPANY",
            addressLine1 = addressData["addressLine1"],
            addressLine2 = addressData["addressLine2"],
            city = addressData["city"],
            state = addressData["state"],
            zip = addressData["zip"],
            terms = null,
            items = findItems(textLines)
        )
    }

    private fun extractShipToBlock(lines: List<String>): String? {
        val startIdx = lines.indexOfFirst {
            val u = it.uppercase()
            u.contains("SHIP TO INFORMATION") ||
                    u.contains("SNO TO INFORMATION") ||
                    u.contains("SHIP TO INFORMAT") ||
                    u.contains("SHIP TO")
        }

        if (startIdx == -1) return null

        val block = mutableListOf<String>()

        for (i in startIdx..minOf(startIdx + 18, lines.lastIndex)) {
            val line = normalizeAddressText(lines[i])
            if (line.isBlank()) continue

            val stop = listOf(
                "SEND INVOICE",
                "ACCOUNTS PAYABLE",
                "PURCHASING AGENT",
                "CUSTOMER PURCHASE ORDER NUMBER",
                "ORDER NOTES AND INSTRUCTIONS",
                "FISHER SCIENTIFIC SUPPLIER NUMBER",
                "SUPPLIER CATALOG",
                "PRODUCT DESCRIPTION",
                "FISHER PO LINE",
                "TOTAL:"
            ).any { line.contains(it) }

            if (stop && block.size >= 3) break
            block += line
        }

        return block.joinToString("\n").ifBlank { null }
    }

    private fun resolveAddressFromShipToBlock(block: String?): Map<String, String>? {
        if (block.isNullOrBlank()) return null
        val text = block.uppercase()

        return when {
            text.contains("LANGDON") || text.contains("DALLAS") || text.contains("75241") ->
                mapOf(
                    "addressLine1" to "4951 LANGDON RD SUITE 170",
                    "city" to "DALLAS",
                    "state" to "TX",
                    "zip" to "75241"
                )

            text.contains("BOWLES") || text.contains("AGAWAM") || text.contains("01001") || text.contains("DLOG1") ->
                mapOf(
                    "addressLine1" to "325 BOWLES ROAD",
                    "city" to "AGAWAM",
                    "state" to "MA",
                    "zip" to "01001"
                )

            text.contains("BICKMORE") ||
                    text.contains("BDICKEORE") ||
                    text.contains("CHINO") ||
                    text.contains("91708") ||
                    text.contains("31708") ||
                    text.contains("31709") ||
                    text.contains("SLYU8") ->
                mapOf(
                    "addressLine1" to "6722 BICKMORE AVENUE",
                    "city" to "CHINO",
                    "state" to "CA",
                    "zip" to "91708"
                )

            text.contains("HORIZON RIDGE") ||
                    text.contains("HORTZOH RIDGE") ||
                    text.contains("SUWANEE") ||
                    text.contains("SUPAHEE") ||
                    text.contains("30024") ||
                    text.contains("300S4") ||
                    text.contains("30054") ->
                mapOf(
                    "addressLine1" to "2775 HORIZON RIDGE COURT",
                    "city" to "SUWANEE",
                    "state" to "GA",
                    "zip" to "30024"
                )

            text.contains("TURNBERRY") || text.contains("HANOVER PARK") || text.contains("60133") ->
                mapOf(
                    "addressLine1" to "4500 TURNBERRY DRIVE",
                    "city" to "HANOVER PARK",
                    "state" to "IL",
                    "zip" to "60133"
                )

            text.contains("SILVER CREST") ||
                    text.contains("NAZARETH") ||
                    text.contains("NACARETH") ||
                    text.contains("18064") ||
                    text.contains("16064") ->
                mapOf(
                    "addressLine1" to "6771 SILVER CREST ROAD",
                    "city" to "NAZARETH",
                    "state" to "PA",
                    "zip" to "18064"
                )

            text.contains("EMPIRE") || text.contains("FLOREN") || text.contains("41042") ->
                mapOf(
                    "addressLine1" to "CDC RECEIVING DEPARTMENT",
                    "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE",
                    "city" to "FLORENCE",
                    "state" to "KY",
                    "zip" to "41042"
                )

            else -> null
        }
    }

    private fun resolveAddressFromStreet(fullText: String): Map<String, String>? {
        val text = fullText.uppercase()

        if (text.contains("LANGDON")) {
            return mapOf(
                "addressLine1" to "4951 LANGDON RD SUITE 170",
                "city" to "DALLAS",
                "state" to "TX",
                "zip" to "75241"
            )
        }

        if (text.contains("BOWLES")) {
            return mapOf(
                "addressLine1" to "325 BOWLES ROAD",
                "city" to "AGAWAM",
                "state" to "MA",
                "zip" to "01001"
            )
        }

        if (text.contains("BICKMORE") || text.contains("BDICKEORE") || text.contains("CHINO")) {
            return mapOf(
                "addressLine1" to "6722 BICKMORE AVENUE",
                "city" to "CHINO",
                "state" to "CA",
                "zip" to "91708"
            )
        }

        if (text.contains("HORIZON RIDGE") || text.contains("HORTZOH RIDGE") || text.contains("SUWANEE") || text.contains("SUPAHEE")) {
            return mapOf(
                "addressLine1" to "2775 HORIZON RIDGE COURT",
                "city" to "SUWANEE",
                "state" to "GA",
                "zip" to "30024"
            )
        }

        if (text.contains("TURNBERRY")) {
            return mapOf(
                "addressLine1" to "4500 TURNBERRY DRIVE",
                "city" to "HANOVER PARK",
                "state" to "IL",
                "zip" to "60133"
            )
        }

        if (text.contains("SILVER CREST") || text.contains("NAZARETH") || text.contains("NACARETH")) {
            return mapOf(
                "addressLine1" to "6771 SILVER CREST ROAD",
                "city" to "NAZARETH",
                "state" to "PA",
                "zip" to "18064"
            )
        }

        if (text.contains("EMPIRE") || text.contains("FLOREN") || text.contains("41042")) {
            return mapOf(
                "addressLine1" to "CDC RECEIVING DEPARTMENT",
                "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE",
                "city" to "FLORENCE",
                "state" to "KY",
                "zip" to "41042"
            )
        }

        return null
    }

    private fun normalizeAddressText(raw: String): String {
        return raw.uppercase()
            .replace("|", " ")
            .replace("BISHER", "FISHER")
            .replace("SCTENTIFIC", "SCIENTIFIC")
            .replace("SCLENTIFIC", "SCIENTIFIC")
            .replace("SCTENTIF TC", "SCIENTIFIC")
            .replace("OOTTONHOOD", "COTTONWOOD")
            .replace("COTTONVYDOD", "COTTONWOOD")
            .replace("COP TORWYOOD", "COTTONWOOD")
            .replace("PITISHURGH", "PITTSBURGH")
            .replace("SUPAHEE", "SUWANEE")
            .replace("HORTZOH", "HORIZON")
            .replace("BDICKEORE", "BICKMORE")
            .replace("NACARETH", "NAZARETH")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun findOrderNumber(fullText: String, lines: List<String>): String? {
        val normalized = normalizeOrderNumberText(fullText)

        Regex("""\bPR\d{7,8}\b""")
            .find(normalized)
            ?.let { return it.value }

        Regex("""PR\s*\d{2}\s*\d{4,5}""")
            .find(normalized)
            ?.let { return it.value.replace(Regex("""\s+"""), "") }

        for (line in lines) {
            val u = normalizeOrderNumberText(line)

            Regex("""\bPR\d{7,8}\b""")
                .find(u)
                ?.let { return it.value }

            Regex("""PR\s*\d{2}\s*\d{4,5}""")
                .find(u)
                ?.let { return it.value.replace(Regex("""\s+"""), "") }
        }

        return null
    }

    private fun normalizeOrderNumberText(value: String): String {
        return value.uppercase()
            .replace("FK", "PR")
            .replace("FR", "PR")
            .replace("PK", "PR")
            .replace("P R", "PR")
            .replace(Regex("""PR\s*41\s+(\d{4,5})"""), "PR41$1")
            .replace(Regex("""PR\s+(\d{7,8})"""), "PR$1")
    }

    private fun findItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (raw in lines) {
            val line = normalizeItemLine(raw)

            if (line.length < 18) continue
            if (line.contains("FISHER PO LINE")) continue
            if (line.contains("PISHER FPO LINE")) continue
            if (!line.contains("|")) continue

            val correctedLine = applyKnownOcrSkuCorrections(line)
            val parts = correctedLine.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val normalizedParts = parts.map { normalizeCandidateSku(it) }
            val precisionSku = normalizedParts.firstOrNull { looksLikePrecisionSku(it) } ?: continue

            val moneyValues = extractMoneyValues(correctedLine)
            val unitPrice = extractUnitPrice(moneyValues) ?: continue

            val quantity = parseFisherQuantity(
                line = correctedLine,
                parts = parts,
                unitPrice = unitPrice,
                moneyValues = moneyValues
            ) ?: 1.0

            val key = "$precisionSku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            val fisherCatalog = parts.firstOrNull { it.matches(Regex("""\d{4,6}""")) }

            val descriptionRaw = parts.firstOrNull { part ->
                val p = part.uppercase().trim()
                val candidateSku = normalizeCandidateSku(p)

                candidateSku != precisionSku &&
                        !looksLikePrecisionSku(candidateSku) &&
                        !p.matches(Regex("""\d+(\.\d+)?""")) &&
                        p !in unitTokens &&
                        !p.matches(Regex("""O?\d{2}-\d{2}-\d{2,4}""")) &&
                        !p.matches(Regex("""\d{4,6}""")) &&
                        !p.contains("SUPPLIER") &&
                        !p.contains("CATALOG")
            } ?: precisionSku

            val mapperDesc = fisherCatalog
                ?.let { ItemMapper.getItemDescription(it) }
                ?.ifBlank { null }

            add(
                item(
                    sku = precisionSku,
                    description = mapperDesc ?: descriptionRaw,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun normalizeItemLine(raw: String): String {
        return raw.uppercase()
            .replace("|", " | ")
            .replace("—", "-")
            .replace("–", "-")
            .replace("~", "-")
            .replace("�", "-")
            .replace("“", "")
            .replace("”", "")
            .replace("¥", "V")
            .replace("§", "S")
            .replace("]", " | ")
            .replace("[", " | ")
            .replace("WHIT", "UNIT")
            .replace("PEI", "PE")
            .replace("FE ", "PE ")
            .replace(" I ", " | ")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""-{2,}"""), "-")
            .trim()
    }

    private fun applyKnownOcrSkuCorrections(input: String): String {
        var line = input

        for ((badOcr, goodSku) in knownOcrErrors) {
            val bad = normalizeItemLine(badOcr)
            if (line.contains(bad)) {
                line = line.replace(bad, goodSku)
            }
        }

        return line
    }

    private fun normalizeCandidateSku(raw: String): String {
        var s = raw.uppercase()
            .replace("—", "-")
            .replace("–", "-")
            .replace("~", "-")
            .replace("�", "-")
            .replace("“", "")
            .replace("”", "")
            .replace("¥", "V")
            .replace(Regex("""[^A-Z0-9-]"""), "")
            .replace(Regex("""-{2,}"""), "-")

        s = when (s) {
            "130-129-100" -> "120-12V-100"
            "120-127-100" -> "120-12V-100"

            "150-127-100" -> "150-12V-100"

            "160-247-100" -> "160-24V-100"
            "160-249-100" -> "160-24V-100"
            "160-249-L00" -> "160-24V-100"
            "160-249-LOO" -> "160-24V-100"
            "160-249-L0O" -> "160-24V-100"
            "1K0-24-100" -> "160-24V-100"

            "175-247-100ERDR" -> "175-24V-100"
            "175-247-100-ERDR" -> "175-24V-100"
            "175-249-100" -> "175-24V-100"
            "175-24V-1L00-EKDE" -> "175-24V-100"
            "175-249-LO0ERDE" -> "175-24V-100"
            "175-249-L00ERDE" -> "175-24V-100"
            "175-249-LOOERDE" -> "175-24V-100"

            "180-247-100" -> "180-24V-100"

            "185-247-100" -> "185-24V-100"
            "1AAS-24AYV-100" -> "185-24V-100"
            "1AAS-24V-100" -> "185-24V-100"

            "PHOOLS-1FB-SO" -> "PH0015-1B-50"
            "PHOOIS-1B-SO" -> "PH0015-1B-50"
            "PHOOIS-1FB-SO" -> "PH0015-1B-50"
            "PHOO15-1B-SO" -> "PH0015-1B-50"
            "PHOO15-1B-50" -> "PH0015-1B-50"
            "PH001S-1B-50" -> "PH0015-1B-50"
            "PHOOIS-1B-50" -> "PH0015-1B-50"
            "PHOOIS-1B-S5O" -> "PH0015-1B-50"

            "PHOO25-1B-5O" -> "PH0025-1B-50"
            "PHOO25-1B-50" -> "PH0025-1B-50"
            "PH0025-1B-5O" -> "PH0025-1B-50"

            "PHSOSO-1B-50" -> "PH5090-1B-50"
            "PH5OSO-1B-50" -> "PH5090-1B-50"
            "PH509O-1B-50" -> "PH5090-1B-50"

            else -> s
        }

        return s
    }

    private fun looksLikePrecisionSku(s: String): Boolean {
        if (s.length < 6) return false
        if (isBadTrailingToken(s)) return false
        if (!s.contains("-")) return false

        return s.matches(Regex("""\d{3}-[A-Z0-9]{2,4}-\d{2,4}""")) ||
                s.matches(Regex("""PH\d{4}-[A-Z0-9]{1,2}-\d{2,3}"""))
    }

    private fun isBadTrailingToken(s: String): Boolean {
        val cleaned = s.uppercase()
        return cleaned.matches(Regex("""O?\d{2}-\d{2}-\d{2,4}""")) ||
                cleaned.matches(Regex("""\d{2}-\d{2}-\d{2,4}"""))
    }

    private fun parseFisherQuantity(
        line: String,
        parts: List<String>,
        unitPrice: Double,
        moneyValues: List<Double>
    ): Double? {
        val normalizedLine = line
            .uppercase()
            .replace("PEI", "PE")
            .replace("FE", "PE")
            .replace("WHIT", "UNIT")
            .replace(Regex("""\s+"""), " ")
            .trim()

        Regex("""\|\s*(\d{1,4})\s*\|\s*(?:PK|PE|PEK|EA|CS|BX|PR)\b""", RegexOption.IGNORE_CASE)
            .find(normalizedLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.let { return it }

        Regex("""\b(\d{1,4})\s*(?:PK|PE|PEK|EA|CS|BX|PR)\b""", RegexOption.IGNORE_CASE)
            .find(normalizedLine.replace("|", " "))
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.let { return it }

        val qtyPart = findQuantityPartFromPipes(parts)

        when (qtyPart) {
            ">" -> return 2.0
            "Q" -> return 9.0
            "I", "L", "l" -> return 1.0
        }

        qtyPart
            ?.replace("O", "0")
            ?.replace("I", "1")
            ?.replace("L", "1")
            ?.toDoubleOrNull()
            ?.let { return it }

        val extended = moneyValues.lastOrNull()
        if (extended != null && unitPrice > 0.0 && extended > unitPrice) {
            val calculated = extended / unitPrice
            val rounded = calculated.roundToInt().toDouble()

            if (rounded > 0.0 && abs(calculated - rounded) <= 0.15) {
                return rounded
            }
        }

        return null
    }

    private fun findQuantityPartFromPipes(parts: List<String>): String? {
        val unitIndex = parts.indexOfFirst { it.trim().uppercase() in unitTokens }
        if (unitIndex <= 0) return null

        for (i in (unitIndex - 1) downTo 0) {
            val part = parts[i].trim().uppercase()
            if (part.isBlank()) continue
            if (looksLikePrecisionSku(normalizeCandidateSku(part))) continue
            if (part.matches(Regex("""\d{4,6}"""))) continue

            if (
                part.matches(Regex("""\d{1,4}""")) ||
                part.matches(Regex("""[QOIL>]""")) ||
                part.matches(Regex("""[IL]\d{1,2}"""))
            ) {
                return part
            }
        }

        return null
    }

    private fun extractMoneyValues(line: String): List<Double> {
        val normalized = line
            .replace(",", ".")
            .replace("S$", "$")
            .replace("S300", "53.00")
            .replace("S 300", "53.00")
            .replace(Regex("""\b(\d{1,4})\s+(\d{2})\b"""), "$1.$2")

        return Regex("""\b\d{1,5}\.\d{2}\b""")
            .findAll(normalized)
            .mapNotNull { it.value.toDoubleOrNull() }
            .filter { it > 0.0 }
            .toList()
    }

    private fun extractUnitPrice(moneyValues: List<Double>): Double? {
        if (moneyValues.isEmpty()) return null

        /*
         * Typical Fisher item rows contain unit price and extended price.
         * If only one money value survived OCR, it is usually the unit price.
         */
        if (moneyValues.size == 1) return moneyValues[0]

        /*
         * Prefer the value before the extended total.
         */
        return moneyValues[moneyValues.size - 2]
            .takeIf { it > 0.0 }
            ?: moneyValues.firstOrNull()
    }

    private val unitTokens = setOf("PK", "PE", "PEK", "EA", "CS", "BX", "PR", "FE")

    private val knownOcrErrors = mapOf(
        "130-129-100" to "120-12V-100",
        "120-127-100" to "120-12V-100",
        "120-127—100" to "120-12V-100",
        "120-127~100" to "120-12V-100",

        "150-127-100" to "150-12V-100",
        "150-127—100" to "150-12V-100",
        "150-127~100" to "150-12V-100",

        "160-247-100" to "160-24V-100",
        "160-249-100" to "160-24V-100",
        "160~—249~L00" to "160-24V-100",
        "160~249~L00" to "160-24V-100",
        "160-249-L00" to "160-24V-100",
        "160-249-LOO" to "160-24V-100",
        "160-249-L0O" to "160-24V-100",
        "1K0-24-100" to "160-24V-100",

        "175-247-100/ERDR" to "175-24V-100",
        "175-247-100ERDR" to "175-24V-100",
        "175-247-100-ERDR" to "175-24V-100",
        "175-249-100" to "175-24V-100",
        "175~249—~LO0”ERDE" to "175-24V-100",
        "175~249—~LO0ERDE" to "175-24V-100",
        "175~249~LO0ERDE" to "175-24V-100",
        "175-249--LO0ERDE" to "175-24V-100",
        "175-249-LO0ERDE" to "175-24V-100",

        "180-247-100" to "180-24V-100",

        "185-247-100" to "185-24V-100",
        "1AAS-24AYV-100" to "185-24V-100",
        "1AAS-24V-100" to "185-24V-100",

        "PHOOLS-1FB-SO" to "PH0015-1B-50",
        "PHOOIS-1B-SO" to "PH0015-1B-50",
        "PHOOIS-1FB-SO" to "PH0015-1B-50",
        "PHOO15-1B-SO" to "PH0015-1B-50",
        "PHOO15-1B-50" to "PH0015-1B-50",
        "PH001S-1B-50" to "PH0015-1B-50",
        "PHOOIS-1B-50" to "PH0015-1B-50",

        "PHOO25-1B—5o" to "PH0025-1B-50",
        "PHOO25-1B-5O" to "PH0025-1B-50",
        "PHOO25-1B-50" to "PH0025-1B-50",
        "PH0025-1B-5O" to "PH0025-1B-50",

        "PHSOSO-1B-50" to "PH5090-1B-50",
        "PH5OSO-1B-50" to "PH5090-1B-50",
        "PH509O-1B-50" to "PH5090-1B-50"
    )
}
