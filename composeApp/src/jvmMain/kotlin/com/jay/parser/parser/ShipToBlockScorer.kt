package com.jay.parser.parser

data class ScoredBlock(
    val block: CandidateBlock,
    val score: Int,
    val reasons: List<String>
)

class ShipToBlockScorer {

    fun score(blocks: List<CandidateBlock>): List<ScoredBlock> {
        return blocks.map { block ->
            val reasons = mutableListOf<String>()
            var score = 0

            if (block.containsVendorFingerprint) {
                score -= 100
                reasons.add("Rejected vendor fingerprint")
            }

            if (block.isAddressLike) {
                score += 20
                reasons.add("Address-like")
            }

            if (block.containsCityStateZip) {
                score += 20
                reasons.add("Contains city/state/zip")
            }

            if (block.containsDockOrBuildingHint) {
                score += 25
                reasons.add("Contains dock/building hint")
            }

            val upper = block.rawText.uppercase()

            if (upper.contains("SHIP TO") || upper.contains("SHIP-TO")) {
                score += 15
                reasons.add("Contains ship-to label")
            }

            if (upper.contains("MEDLINE")) {
                score += 20
                reasons.add("Contains customer name clue")
            }

            if (upper.contains("TRANSSHIP") || upper.contains("CROSS DOCK")) {
                score += 20
                reasons.add("Contains transship/cross dock clue")
            }

            if (upper.contains("TOWNLINE")) {
                score += 10
                reasons.add("Contains street clue")
            }

            if (upper.contains("VENDOR #")) {
                score -= 20
                reasons.add("Contains vendor marker")
            }

            if (upper.contains("PURCHASE ORDER")) {
                score -= 15
                reasons.add("Contains document title text")
            }

            if (upper.startsWith("PAGE ")) {
                score -= 20
                reasons.add("Page marker")
            }

            ScoredBlock(
                block = block,
                score = score,
                reasons = reasons
            )
        }.sortedByDescending { it.score }
    }
}