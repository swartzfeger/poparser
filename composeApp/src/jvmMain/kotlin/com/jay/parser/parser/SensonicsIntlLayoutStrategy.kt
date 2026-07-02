package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class SensonicsIntlLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "SENSONICS INTL"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("SENSONICS") &&
                text.contains("PURCHASE ORDER") &&
                text.contains("411 SOUTH BLACK HORSE PIKE") &&
                text.contains("HADDON HEIGHTS")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("SENSONICS")) score += 120
        if (text.contains("PURCHASE ORDER")) score += 80
        if (text.contains("411 SOUTH BLACK HORSE PIKE")) score += 80
        if (text.contains("HADDON HEIGHTS")) score += 80
        if (text.contains("PRC-TASTE-KIT-27-V2")) score += 60
        if (text.contains("PRC-TASTE-KIT-53")) score += 60
        if (text.contains("SPC-KITA-1B-53")) score += 60
        if (text.contains("SPC-CAF088")) score += 40
        if (text.contains("SPC-CIT400")) score += 40
        if (text.contains("SPC-MSG135")) score += 40
        if (text.contains("SPC-NACL250")) score += 40
        if (text.contains("SPC-SUC400")) score += 40
        if (text.contains("SPC-CTRL")) score += 40
        if (text.contains("PRC-CNTRL")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }

        return ParsedPdfFields(
            customerName = "SENSONICS INTL",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = "Sensonics",
            addressLine1 = "411 South Black Horse Pike",
            addressLine2 = null,
            city = "Haddon Heights",
            state = "NJ",
            zip = "08035",
            terms = null,
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i].trim()

            if (line.contains("P.O.No.", ignoreCase = true) ||
                line.contains("P.O. No.", ignoreCase = true) ||
                line.contains("PO No.", ignoreCase = true)
            ) {
                val next = lines.getOrNull(i + 1)?.trim().orEmpty()

                Regex("""^\d{1,2}/\d{1,2}/\d{4}\s+(\d+)$""")
                    .find(next)
                    ?.groupValues
                    ?.get(1)
                    ?.let { return it }

                Regex("""\b(?:P\.?O\.?\s*No\.?:?\s*)?(\d+)\b""", RegexOption.IGNORE_CASE)
                    .findAll(next)
                    .map { it.groupValues[1] }
                    .lastOrNull()
                    ?.let { return it }
            }

            Regex("""P\.?O\.?\s*No\.?:?\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.let { return it }
        }

        // Fallback for the Sensonics table pattern: "1/23/2026 943" or "5/11/2026 3".
        lines.forEach { line ->
            Regex("""^\d{1,2}/\d{1,2}/\d{4}\s+(\d+)$""")
                .find(line.trim())
                ?.groupValues
                ?.get(1)
                ?.let { return it }
        }

        return null
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val rowMatch = itemRowRegex.find(line)

            if (rowMatch != null) {
                val rawSku = rowMatch.groupValues[1].trim().uppercase()
                val firstDesc = rowMatch.groupValues[2].trim()
                val quantity = rowMatch.groupValues[3].replace(",", "").toDoubleOrNull()
                val unitPrice = rowMatch.groupValues[5].replace(",", "").toDoubleOrNull()

                val descParts = mutableListOf(firstDesc)
                var j = i + 1

                while (j < lines.size) {
                    val next = lines[j].replace(Regex("""\s+"""), " ").trim()

                    if (itemRowRegex.matches(next) || isFooterOrHeaderLine(next)) {
                        break
                    }

                    if (next.isNotBlank()) {
                        descParts.add(next)
                    }

                    j++
                }

                val rawDescription = cleanupDescription(descParts.joinToString(" "))
                val sku = mapSensonicsSku(rawSku, rawDescription)
                val description = ItemMapper.getItemDescription(sku).ifBlank { rawDescription }

                if (quantity != null && unitPrice != null) {
                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = description,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }

                i = j
                continue
            }

            i++
        }

        return items
    }

    private val itemRowRegex = Regex(
        """^((?:PRC-CNTRL\s*P|[A-Z0-9][A-Z0-9-]+)(?:\.\.\.)?)\s+(.+?)\s+(\d+(?:\.\d+)?)\s+(EA|EACH)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})$""",
        RegexOption.IGNORE_CASE
    )

    private fun isFooterOrHeaderLine(line: String): Boolean {
        return line.startsWith("TOTAL", true) ||
                line.startsWith("TERMS", true) ||
                line.startsWith("PLEASE CALL", true) ||
                line.startsWith("PURCHASE ORDER", true) ||
                line.startsWith("VENDOR", true) ||
                line.startsWith("SHIP DATE", true) ||
                line.startsWith("ITEM DESCRIPTION", true) ||
                line.startsWith("DATE P.O", true)
    }

    private fun mapSensonicsSku(rawSku: String, description: String): String {
        val compactSku = rawSku.uppercase()
            .replace("...", "")
            .replace("…", "")
            .replace(Regex("""[^A-Z0-9]+"""), "")

        return when {
            compactSku.startsWith("PRCTASTEKIT27V2") -> "SPC-KITA-1B-27"
            compactSku.startsWith("PRCTASTEKIT53") -> "SPC-KITA-1B-53"
            compactSku.startsWith("SPCKITA1B53") -> "SPC-KITA-1B-53"

            compactSku.startsWith("PRCCNTRLP") || description.contains("Control paper", ignoreCase = true) ->
                "PRC-CNTRL PAPER"

            compactSku.startsWith("SPCCAF0881") || description.contains("Caffine", ignoreCase = true) || description.contains("Caffeine", ignoreCase = true) ->
                "SPC-CAF088-1B-50"

            compactSku.startsWith("SPCCIT400B") || description.contains("Citric Acid", ignoreCase = true) ->
                "SPC-CIT400-B-50"

            compactSku.startsWith("SPCMSG1351") || description.contains("UMAMI", ignoreCase = true) || description.contains("MSG", ignoreCase = true) ->
                "SPC-MSG135-1B-50"

            compactSku.startsWith("SPCNACL250") || description.contains("SODIUM CHLORIDE", ignoreCase = true) ->
                "SPC-NACL250-1B-50"

            compactSku.startsWith("SPCSUC4001") || description.contains("SUCROSE", ignoreCase = true) ->
                "SPC-SUC400-1B-50"

            compactSku.startsWith("SPCCTRL1B") || description.contains("Control Strips", ignoreCase = true) ->
                "SPC-CTRL-1B-50"

            else -> rawSku.uppercase()
                .replace("...", "")
                .replace("…", "")
                .trim(' ', '.', '-')
        }
    }

    private fun cleanupDescription(raw: String): String {
        return raw
            .replace("TasteStripKit", "Taste Strip Kit")
            .replace("TasteStrip", "Taste Strip")
            .replace("Controlpaper", "Control paper")
            .replace("CaffineStrips", "Caffine Strips")
            .replace("CaffeineStrips", "Caffeine Strips")
            .replace("CitricAcidStrips", "Citric Acid Strips")
            .replace("UMAMITasteTestStrips", "UMAMI Taste Test Strips")
            .replace("SODIUMCHLORIDETASTESTRIPS", "SODIUM CHLORIDE TASTE STRIPS")
            .replace("SUCROSESTRIPS", "SUCROSE STRIPS")
            .replace("ControlStrips", "Control Strips")
            .replace("SEQNumbers", "SEQ Numbers ")
            .replace("SEQ#", "SEQ#")
            .replace(Regex("""(?i)\b1OMM\b"""), "10MM")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-')
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace("SENSONICS, INC.", "SENSONICS")
            .replace("PRECISIONLABORATORIES", "PRECISION LABORATORIES")
            .replace("411SOUTHBLACKHORSEPIKE", "411 SOUTH BLACK HORSE PIKE")
            .replace("HADDONHEIGHTS,NJ 08035", "HADDON HEIGHTS, NJ 08035")
            .replace("HADDONHEIGHTS,PA 08035", "HADDON HEIGHTS, NJ 08035")
            .replace("P.O.NO.", "P O NO")
            .replace("PURCHASEORDER", "PURCHASE ORDER")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
