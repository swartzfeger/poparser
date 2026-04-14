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

        // Attempt to resolve the perfect address using the extracted Zip Code
        val addressData = resolveAddressFromZip(fullText)

        return ParsedPdfFields(
            customerName = "FISHER SCIENTIFIC CO",
            orderNumber = findOrderNumber(fullText, textLines),
            shipToCustomer = "FISHER SCIENTIFIC COMPANY",
            addressLine1 = addressData["addressLine1"]?.takeIf { it.isNotBlank() } ?: findDynamicAddressLine1(textLines),
            addressLine2 = addressData["addressLine2"]?.takeIf { it.isNotBlank() },
            city = addressData["city"]?.takeIf { it.isNotBlank() },
            state = addressData["state"]?.takeIf { it.isNotBlank() } ?: findDynamicState(fullText),
            zip = addressData["zip"]?.takeIf { it.isNotBlank() } ?: findDynamicZip(fullText),
            terms = null,
            items = findItems(textLines)
        )
    }

    /**
     * Maps the unique Ship-To zip code to the exact clean address block.
     * Bypasses OCR text drops and severe mangling.
     */
    private fun resolveAddressFromZip(fullText: String): Map<String, String> {
        val matches = Regex("""\b([A-Z]{2})[.\s]+(\d{3,5})\b""").findAll(fullText)

        for (match in matches) {
            var state = match.groupValues[1].uppercase()
            val rawZip = match.groupValues[2]

            // Fix common OCR state letter swaps
            if (state == "HA") state = "MA"
            if (state == "FA") state = "PA"

            // Expanded Ignore List to catch OCR misreads of the corporate Zips
            val ignoreZips = setOf("15275", "16275", "18275", "15230", "16230", "18230", "86326", "6326")
            if (rawZip in ignoreZips || state == "AZ") continue

            // Fix common OCR zip number swaps
            val cleanZip = when (rawZip) {
                "31708" -> "91708"
                "164" -> "18064"
                else -> rawZip
            }

            return when (cleanZip) {
                "75241" -> mapOf("addressLine1" to "4951 LANGDON RD SUITE 170", "city" to "DALLAS", "state" to "TX", "zip" to "75241")
                "01001" -> mapOf("addressLine1" to "325 BOWLES ROAD", "city" to "AGAWAM", "state" to "MA", "zip" to "01001")
                "91708" -> mapOf("addressLine1" to "6722 BICKMORE AVENUE", "city" to "CHINO", "state" to "CA", "zip" to "91708")
                "30024" -> mapOf("addressLine1" to "2775 HORIZON RIDGE COURT", "city" to "SUWANEE", "state" to "GA", "zip" to "30024")
                "41042" -> mapOf("addressLine1" to "CDC RECEIVING DEPARTMENT", "addressLine2" to "SUITE B, 7383 EMPIRE DRIVE", "city" to "FLORENCE", "state" to "KY", "zip" to "41042")
                "60133" -> mapOf("addressLine1" to "4500 TURNBERRY DRIVE", "city" to "HANOVER PARK", "state" to "IL", "zip" to "60133")
                "18064" -> mapOf("addressLine1" to "6771 SILVER CREST ROAD", "city" to "NAZARETH", "state" to "PA", "zip" to "18064")
                else -> mapOf("addressLine1" to "", "addressLine2" to "", "city" to "", "state" to state, "zip" to cleanZip)
            }
        }
        return emptyMap()
    }

    /**
     * Fallback extractor if Fisher ships to a brand new facility not in the map.
     */
    private fun findDynamicAddressLine1(lines: List<String>): String? {
        val startIdx = lines.indexOfFirst { line ->
            val upper = line.uppercase()
            upper.contains("FISHER SC") && !upper.contains("LLC") && !upper.contains("LLG") && !upper.contains("LEO") && !upper.contains("INDUSTRY") && !upper.contains("PITTSBURGH")
        }

        if (startIdx == -1) return null

        for (i in 1..6) {
            if (startIdx + i > lines.lastIndex) break
            var line = lines[startIdx + i].uppercase()

            line = line.replace(Regex("""415\s+A[IT]RPARK\s+DRIVE"""), "")
            line = line.replace(Regex("""[COQ][OQ]TT[OQ]N[VW]OOD.*"""), "")
            line = line.replace(Regex("""AZ\.?\s*[8S${'$'}]?6326"""), "") // Escaped Kotlin interpolation fix
            line = line.replace(Regex("""T:\s*800\s*733-0266"""), "")
            line = line.replace(Regex("""F:\s*928\s*649-2306"""), "")
            line = line.replace(Regex("""PRECISION LABORATORIES.*"""), "")
            line = line.replace(Regex("""P[O0][.,]?\s*BOX\s*1768"""), "")
            line = line.replace(Regex("""R[O0][.,]?\s*BOX\s*1768"""), "")
            line = line.replace(Regex("""PITTSBURGH,?\s*PA\.?\s*1[568]230"""), "")
            line = line.replace("ADDRESS: FISHER SCIENTIFIC", "")
            line = line.replace("EMAIL: APVENDOR@THERMOFISHER.COM", "")
            line = line.replace("EMAIL: APVENDOR@THERMOFISHERCEM", "")

            line = line.replace("|", "").replace("(", "").replace(")", "").replace("[", "").replace("]", "").trim()
            line = line.replace(Regex("""\s+"""), " ")

            if (line.length > 5 && (line.matches(Regex(""".*\d+.*""")) || line.contains("CDC") || line.contains("SUITE"))) {
                return line
            }
        }
        return null
    }

    private fun findDynamicState(fullText: String): String? {
        val matches = Regex("""\b([A-Z]{2})[.\s]+(\d{5})\b""").findAll(fullText)
        val ignoreZips = setOf("15275", "16275", "18275", "15230", "16230", "18230", "86326", "6326")
        for (match in matches) {
            val state = match.groupValues[1]
            val zip = match.groupValues[2]
            if (zip in ignoreZips || state == "AZ") continue
            return state.replace("HA", "MA").replace("FA", "PA")
        }
        return null
    }

    private fun findDynamicZip(fullText: String): String? {
        val matches = Regex("""\b([A-Z]{2})[.\s]+(\d{5})\b""").findAll(fullText)
        val ignoreZips = setOf("15275", "16275", "18275", "15230", "16230", "18230", "86326", "6326")
        for (match in matches) {
            val state = match.groupValues[1]
            val zip = match.groupValues[2]
            if (zip in ignoreZips || state == "AZ") continue
            return zip
        }
        return null
    }

    private fun findOrderNumber(fullText: String, lines: List<String>): String? {
        val prMatch = Regex("""\b[P|F][R|K]\d{6,8}\b""", RegexOption.IGNORE_CASE).find(fullText)
        if (prMatch != null) return prMatch.value.uppercase().replace("FK", "PR").replace("FR", "PR").replace("PK", "PR")

        val labelIdx = lines.indexOfFirst { it.uppercase().contains("CUSTOMER PURCHASE ORDER") }
        if (labelIdx >= 0) {
            for (i in labelIdx until (labelIdx + 15).coerceAtMost(lines.lastIndex)) {
                val match = Regex("""\b(\d{6,8})\b""").find(lines[i])
                if (match != null) {
                    val candidate = match.value
                    if (candidate.startsWith("0") && (candidate.endsWith("25") || candidate.endsWith("26") || candidate.endsWith("27"))) continue
                    return candidate
                }
            }
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
                .replace("“", "/")
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (line.length < 20) continue

            val knownOcrErrors = mapOf(
                "130-129-100" to "120-12V-100",
                "175-247-100/ERDR" to "175-24V-100",
                "PHOOLS-1FB-SO" to "PH0015-1B-50",
                "1K0-24-100" to "160-24V-100",
                "160-247-100" to "160-24V-100",
                "1AAS-24AYV-100" to "185-24V-100",
                "1AAS-24V-100" to "185-24V-100",
                "03-04-Z8" to "",
                "O3-O4-Z8" to ""
            )

            for ((badOcr, goodSku) in knownOcrErrors) {
                if (line.contains(badOcr)) {
                    line = line.replace(badOcr, goodSku)
                }
            }

            val parts = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

            var precisionSku = parts.find { part ->
                part.contains("-") && part.matches(Regex("""[A-Z0-9-]{6,}"""))
            }?.replace(Regex("""[^A-Z0-9-]"""), "")

            if (precisionSku == null) {
                precisionSku = parts.find { it.matches(Regex("""\d{3,}-\d{2,3}[A-Z]?[-]?\d{2,3}""")) }
                    ?.replace(Regex("""[^A-Z0-9-]"""), "")
            }

            if (precisionSku == null || precisionSku.length < 5 || seen.contains(precisionSku)) continue

            val fisherCatalog = parts.find { it.matches(Regex("""\d{4,}""")) }
                ?.replace(Regex("""[^A-Z0-9-]"""), "")

            val cleanLineForQty = line.replace("|", "")
            val qtyRegex = Regex("""\b(\d{1,4})\s*[)|\]]?\s*(?:PK|PE|PEK|EA|CS|BX)\b""", RegexOption.IGNORE_CASE)
            val inlineQtyMatch = qtyRegex.find(cleanLineForQty)

            val qty = if (inlineQtyMatch != null) {
                inlineQtyMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            } else {
                val uomIndex = parts.indexOfFirst { it.uppercase().replace(Regex("[^A-Z]"), "") in setOf("PK", "PE", "PEK", "EA", "CS", "BX") }
                if (uomIndex > 0) {
                    parts[uomIndex - 1].replace(Regex("""[^\d]"""), "").toDoubleOrNull() ?: 1.0
                } else 1.0
            }

            val priceCandidates = line.split(" ")
                .map { it.replace(",", ".") }
                .mapNotNull { it.toDoubleOrNull() }
                .filter { it in 0.01..999.99 }

            val unitPrice = priceCandidates.lastOrNull()

            if (unitPrice != null && unitPrice > 0) {
                val descriptionRaw = parts.getOrNull(1)?.trim() ?: precisionSku
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