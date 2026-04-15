package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class FisherScientificCoLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Fisher Scientific Co"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString(" ").replace("|", " ").uppercase()
        return text.contains("FISHER") &&
                (text.contains("PRECISION") || text.contains("PRECISTON") || text.contains("LABORATORIES"))
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()
        var score = 0
        if (text.contains("FISHER")) score += 100
        if (text.contains("PRECISION") || text.contains("PRECISTON") || text.contains("LABORATORIES")) score += 80
        if (text.contains("ORDER NOTES AND INSTRUCTIONS")) score += 60
        if (text.contains("CATALOG")) score += 40
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
            u.contains("SHIP TO INFORMATION") || u.contains("SNO TO INFORMATION")
        }
        if (startIdx == -1) return null

        val block = mutableListOf<String>()
        for (i in startIdx..minOf(startIdx + 14, lines.lastIndex)) {
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
                "PRODUCT DESCRIPTION"
            ).any { line.contains(it) }

            if (stop) break
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

            text.contains("FLOREN") || text.contains("41042") ->
                mapOf(
                    "addressLine1" to "CDC RECEIVING DEPARTMENT",
                    "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE",
                    "city" to "FLORENCE",
                    "state" to "KY",
                    "zip" to "41042"
                )

            text.contains("BOWLES") || text.contains("AGAWAM") || text.contains("01001") ->
                mapOf(
                    "addressLine1" to "325 BOWLES ROAD",
                    "city" to "AGAWAM",
                    "state" to "MA",
                    "zip" to "01001"
                )

            text.contains("BICKMORE") || text.contains("CHINO") || text.contains("91708") || text.contains("31708") || text.contains("31709") ->
                mapOf(
                    "addressLine1" to "6722 BICKMORE AVENUE",
                    "city" to "CHINO",
                    "state" to "CA",
                    "zip" to "91708"
                )

            text.contains("HORIZON RIDGE") || text.contains("SUWANEE") || text.contains("SUPAHEE") || text.contains("30024") || text.contains("300S4") || text.contains("30054") ->
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

            text.contains("SILVER CREST") || text.contains("NAZARETH") || text.contains("18064") || text.contains("16064") ->
                mapOf(
                    "addressLine1" to "6771 SILVER CREST ROAD",
                    "city" to "NAZARETH",
                    "state" to "PA",
                    "zip" to "18064"
                )

            text.contains("EMPIRE") || text.contains("FLORENCE") || text.contains("41042") ->
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
        if (text.contains("LANGDON")) return mapOf("addressLine1" to "4951 LANGDON RD SUITE 170", "city" to "DALLAS", "state" to "TX", "zip" to "75241")
        if (text.contains("FLOREN") || text.contains("41042")) {
            return mapOf(
                "addressLine1" to "CDC RECEIVING DEPARTMENT",
                "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE",
                "city" to "FLORENCE",
                "state" to "KY",
                "zip" to "41042"
            )
        }
        if (text.contains("BOWLES")) return mapOf("addressLine1" to "325 BOWLES ROAD", "city" to "AGAWAM", "state" to "MA", "zip" to "01001")
        if (text.contains("BICKMORE")) return mapOf("addressLine1" to "6722 BICKMORE AVENUE", "city" to "CHINO", "state" to "CA", "zip" to "91708")
        if (text.contains("HORIZON RIDGE") || text.contains("SUPAHEE")) return mapOf("addressLine1" to "2775 HORIZON RIDGE COURT", "city" to "SUWANEE", "state" to "GA", "zip" to "30024")
        if (text.contains("EMPIRE")) return mapOf("addressLine1" to "CDC RECEIVING DEPARTMENT", "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE", "city" to "FLORENCE", "state" to "KY", "zip" to "41042")
        if (text.contains("TURNBERRY")) return mapOf("addressLine1" to "4500 TURNBERRY DRIVE", "city" to "HANOVER PARK", "state" to "IL", "zip" to "60133")
        if (text.contains("SILVER CREST")) return mapOf("addressLine1" to "6771 SILVER CREST ROAD", "city" to "NAZARETH", "state" to "PA", "zip" to "18064")
        return null
    }

    private fun normalizeAddressText(raw: String): String {
        return raw.uppercase()
            .replace("|", " ")
            .replace("BISHER", "FISHER")
            .replace("SCTENTIFIC", "SCIENTIFIC")
            .replace("SCLENTIFIC", "SCIENTIFIC")
            .replace("OOTTONHOOD", "COTTONWOOD")
            .replace("COTTONVYDOD", "COTTONWOOD")
            .replace("PITISHURGH", "PITTSBURGH")
            .replace("SUPAHEE", "SUWANEE")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun findOrderNumber(fullText: String, lines: List<String>): String? {

        val normalized = fullText.uppercase()
            .replace("FK", "PR")
            .replace("FR", "PR")
            .replace("PK", "PR")
            .replace(Regex("""PR\s*41\s+(\d{4})"""), "PR41$1")

        // 1. Strong global match
        Regex("""\bPR\d{7,8}\b""")
            .find(normalized)
            ?.let { return it.value }

        // 2. Catch split format like "PR41 40884"
        Regex("""PR\s*\d{2}\s*\d{4,5}""")
            .find(normalized)
            ?.let { return it.value.replace(" ", "") }

        // 3. Search line-by-line (important for OCR fragments)
        for (line in lines) {
            val u = line.uppercase()
                .replace("FK", "PR")
                .replace("FR", "PR")
                .replace("PK", "PR")
                .replace(Regex("""PR\s*41\s+(\d{4})"""), "PR41$1")

            Regex("""\bPR\d{7,8}\b""")
                .find(u)
                ?.let { return it.value }
        }

        return null
    }

    private fun findItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (raw in lines) {
            var line = raw.uppercase()
                .replace("|", " | ")
                .replace("—", "-")
                .replace("~", "-")
                .replace("“", "")
                .replace("”", "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (line.length < 20) continue
            if (line.contains("FISHER PO LINE")) continue

            val knownOcrErrors = mapOf(
                "130-129-100" to "120-12V-100",
                "175-247-100/ERDR" to "175-24V-100",
                "175-249-100" to "175-24V-100",
                "175-249--LO0ERDE" to "175-24V-100",
                "175-249-LO0ERDE" to "175-24V-100",
                "PHOOLS-1FB-SO" to "PH0015-1B-50",
                "PHOOIS-1B-SO" to "PH0015-1B-50",
                "1K0-24-100" to "160-24V-100",
                "160-247-100" to "160-24V-100",
                "180-247-100" to "180-24V-100",
                "185-247-100" to "185-24V-100",
                "1AAS-24AYV-100" to "185-24V-100",
                "1AAS-24V-100" to "185-24V-100"
            )

            for ((badOcr, goodSku) in knownOcrErrors) {
                if (line.contains(badOcr)) line = line.replace(badOcr, goodSku)
            }

            val parts = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

            fun normalizeCandidateSku(raw: String): String {
                var s = raw.uppercase()
                    .replace("—", "-")
                    .replace("~", "-")
                    .replace("“", "")
                    .replace("”", "")
                    .replace(Regex("""[^A-Z0-9-]"""), "")

                // Fisher-specific OCR fixes
                s = when (s) {
                    "PHOOIS-1B-SO" -> "PH0015-1B-50"
                    "PHOOLS-1FB-SO" -> "PH0015-1B-50"
                    "PHOOIS-1FB-SO" -> "PH0015-1B-50"
                    "130-129-100" -> "120-12V-100"
                    "175-247-100ERDR" -> "175-24V-100"
                    "175-249-100" -> "175-24V-100"
                    "175-249--LO0ERDE" -> "175-24V-100"
                    "175-249-LO0ERDE" -> "175-24V-100"
                    "1K0-24-100" -> "160-24V-100"
                    "160-247-100" -> "160-24V-100"
                    "180-247-100" -> "180-24V-100"
                    "185-247-100" -> "185-24V-100"
                    "1AAS-24AYV-100" -> "185-24V-100"
                    "1AAS-24V-100" -> "185-24V-100"
                    else -> s
                }

                return s
            }

            fun isBadTrailingToken(s: String): Boolean {
                val cleaned = s.uppercase()
                return cleaned.matches(Regex("""O?\d{2}-\d{2}-\d{2,4}""")) ||
                        cleaned.matches(Regex("""\d{2}-\d{2}-\d{2,4}"""))
            }

            fun looksLikePrecisionSku(s: String): Boolean {
                if (s.length < 6) return false
                if (isBadTrailingToken(s)) return false
                if (!s.contains("-")) return false

                // Must start with either a 3-digit family or PH...
                return s.matches(Regex("""\d{3}-[A-Z0-9]{2,4}-\d{2,4}""")) ||
                        s.matches(Regex("""PH\d{4}-[A-Z0-9]{1,2}-\d{2,3}"""))
            }

            val normalizedParts = parts.map { normalizeCandidateSku(it) }

            val precisionSku = normalizedParts.firstOrNull { looksLikePrecisionSku(it) }

            if (precisionSku == null || seen.contains(precisionSku)) continue

            val fisherCatalog = parts.firstOrNull { it.matches(Regex("""\d{4,5}""")) }

            val qty = Regex("""\b(\d{1,4})\s*(?:PK|PE|PEK|EA|CS|BX|PR)\b""")
                .find(line.replace("|", " "))
                ?.groupValues?.get(1)
                ?.toDoubleOrNull()
                ?: 1.0

            val money = Regex("""\b\d{1,3}\.\d{2}\b""")
                .findAll(line.replace(",", "."))
                .map { it.value.toDouble() }
                .toList()

            val unitPrice = when {
                money.size >= 2 -> money[money.size - 2]
                money.size == 1 -> money[0]
                else -> null
            }

            if (unitPrice != null && unitPrice > 0) {
                val descriptionRaw = parts.firstOrNull { part ->
                    val p = part.uppercase().trim()
                    p != precisionSku &&
                            !p.matches(Regex("""\d+(\.\d+)?""")) &&
                            p !in setOf("PK", "PE", "PEK", "EA", "CS", "BX", "PR") &&
                            !p.matches(Regex("""O?\d{2}-\d{2}-\d{2,4}"""))
                } ?: precisionSku
                val mapperDesc = fisherCatalog?.let { ItemMapper.getItemDescription(it) }?.ifBlank { null }

                add(
                    item(
                        sku = precisionSku,
                        description = mapperDesc ?: descriptionRaw,
                        quantity = qty,
                        unitPrice = unitPrice
                    )
                )
                seen.add(precisionSku)
            }
        }
    }
}