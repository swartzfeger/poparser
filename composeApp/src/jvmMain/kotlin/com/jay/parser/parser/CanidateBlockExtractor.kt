package com.jay.parser.parser

class CandidateBlockExtractor {

    fun extract(lines: List<String>): List<CandidateBlock> {
        val cleanedLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        for (line in cleanedLines) {
            if (isSeparator(line)) {
                flushBlock(current, blocks)
                current = mutableListOf()
                continue
            }

            if (current.isEmpty()) {
                current.add(line)
                continue
            }

            val previous = current.last()

            if (shouldStartNewBlock(previous, line)) {
                flushBlock(current, blocks)
                current = mutableListOf(line)
            } else {
                current.add(line)
            }
        }

        flushBlock(current, blocks)

        return blocks
            .map { toCandidateBlock(it) }
            .filter { it.lines.isNotEmpty() }
    }

    private fun flushBlock(current: List<String>, blocks: MutableList<List<String>>) {
        if (current.isNotEmpty()) {
            blocks.add(current.toList())
        }
    }

    private fun isSeparator(line: String): Boolean {
        return line.matches(Regex("""^[=_*\- ]{5,}$"""))
    }

    private fun shouldStartNewBlock(previous: String, current: String): Boolean {
        if (looksLikeHeader(current)) return true
        if (looksLikeHeader(previous)) return true

        if (looksLikeStandaloneLabel(current)) return true

        return false
    }

    private fun looksLikeHeader(line: String): Boolean {
        val upper = line.uppercase()

        return upper.contains("SHIP TO") ||
                upper.contains("SHIP-TO") ||
                upper.contains("BILL TO") ||
                upper.contains("PURCHASE ORDER") ||
                upper.contains("VENDOR") ||
                upper.contains("DELIVERY APPOINTMENT") ||
                upper.contains("CONTACT PERSON") ||
                upper.contains("LINE MATERIAL DESCRIPTION") ||
                upper.contains("ORDER QTY")
    }

    private fun looksLikeStandaloneLabel(line: String): Boolean {
        val upper = line.uppercase()

        return upper == "US" ||
                upper == "USA" ||
                upper == "PAGE 1 OF 1" ||
                upper.startsWith("PAGE ")
    }

    private fun toCandidateBlock(lines: List<String>): CandidateBlock {
        val rawText = lines.joinToString("\n")
        val normalized = VendorFingerprint.normalize(rawText)

        return CandidateBlock(
            lines = lines,
            normalizedText = normalized,
            isAddressLike = isAddressLike(lines),
            containsVendorFingerprint = VendorFingerprint.containsVendorFingerprint(rawText),
            containsCityStateZip = containsCityStateZip(lines),
            containsDockOrBuildingHint = containsDockOrBuildingHint(lines)
        )
    }

    private fun isAddressLike(lines: List<String>): Boolean {
        val joined = lines.joinToString(" ").uppercase()

        val hasStreetNumber = Regex("""\b\d{2,6}\b""").containsMatchIn(joined)
        val hasStreetWord = listOf(
            "RD", "ROAD", "ST", "STREET", "AVE", "AVENUE",
            "DR", "DRIVE", "BLVD", "LANE", "LN", "WAY",
            "COURT", "CT", "HWY", "HIGHWAY"
        ).any { joined.contains(it) }

        val hasCityStateZip = containsCityStateZip(lines)

        return (hasStreetNumber && hasStreetWord) || hasCityStateZip
    }

    private fun containsCityStateZip(lines: List<String>): Boolean {
        val regex = Regex("""\b[A-Z][A-Z .'-]+,\s*[A-Z]{2}\s+\d{5}(?:-\d{4})?\b""")
        return lines.any { regex.containsMatchIn(it.uppercase()) }
    }

    private fun containsDockOrBuildingHint(lines: List<String>): Boolean {
        val joined = lines.joinToString(" ").uppercase()

        return listOf(
            "DOCK",
            "BUILDING",
            "BLDG",
            "TRANSSHIP",
            "CROSS DOCK",
            "RECEIVING",
            "ATTN",
            "ATTENTION"
        ).any { joined.contains(it) }
    }
}