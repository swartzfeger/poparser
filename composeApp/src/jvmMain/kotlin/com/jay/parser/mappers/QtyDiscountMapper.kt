package com.jay.parser.mappers

import com.jay.parser.masterdata.MasterDataStore
import com.jay.parser.masterdata.MasterQtyDiscountBreak
import com.jay.parser.masterdata.MasterQtyDiscountRule
import kotlin.math.round

object QtyDiscountMapper {

    data class QtyDiscountResult(
        val unitPrice: Double,
        val discountPercent: Double,
        val matchedRule: MasterQtyDiscountRule?
    )

    data class QtyDiscountRule(
        val customerId: String,
        val itemId: String,
        val qtyDiscountId: String,
        val priceLevel: String?,
        val breaks: List<QtyDiscountBreak>
    )

    data class QtyDiscountBreak(
        val minQty: Double,
        val discountPercent: Double
    )

    fun applyQtyDiscount(
        customerId: String?,
        sku: String?,
        quantity: Double,
        unitPrice: Double,
        priceLevel: String?
    ): QtyDiscountResult {
        if (sku.isNullOrBlank() || quantity <= 0.0 || unitPrice <= 0.0) {
            return QtyDiscountResult(unitPrice, 0.0, null)
        }

        val normalizedCustomerId = normalize(customerId)
        val normalizedSku = normalizeSku(sku)
        val normalizedPriceLevel = normalize(priceLevel)

        val rules = MasterDataStore.current().qtyDiscountRules

        val customerSpecificMatch = rules
            .filter { normalize(it.customerId) != ALL_CUSTOMERS }
            .filter { rule ->
                normalize(rule.customerId) == normalizedCustomerId &&
                        rule.matchesSku(normalizedSku) &&
                        rule.matchesPriceLevel(normalizedPriceLevel)
            }
            .bestRuleFor(quantity)

        val globalMatch = rules
            .filter { normalize(it.customerId) == ALL_CUSTOMERS }
            .filter { rule ->
                rule.matchesSku(normalizedSku) &&
                        rule.matchesPriceLevel(normalizedPriceLevel)
            }
            .bestRuleFor(quantity)

        val match = customerSpecificMatch ?: globalMatch ?: return QtyDiscountResult(unitPrice, 0.0, null)

        val discountedPrice = roundMoney(unitPrice * (1.0 - match.breakPoint.discountPercent))

        return QtyDiscountResult(
            unitPrice = discountedPrice,
            discountPercent = match.breakPoint.discountPercent,
            matchedRule = match.rule
        )
    }

    private data class RuleMatch(
        val rule: MasterQtyDiscountRule,
        val breakPoint: MasterQtyDiscountBreak
    )

    private fun List<MasterQtyDiscountRule>.bestRuleFor(quantity: Double): RuleMatch? {
        return this
            .mapNotNull { rule ->
                val bestBreak = rule.breaks
                    .filter { quantity >= it.minQty }
                    .maxByOrNull { it.minQty }

                bestBreak?.let { RuleMatch(rule, it) }
            }
            .maxWithOrNull(
                compareBy<RuleMatch> { it.breakPoint.minQty }
                    .thenBy { it.breakPoint.discountPercent }
            )
    }

    private fun MasterQtyDiscountRule.matchesSku(normalizedSku: String): Boolean {
        return normalizeSku(itemId) == normalizedSku ||
                normalizeSku(qtyDiscountId) == normalizedSku
    }

    private fun MasterQtyDiscountRule.matchesPriceLevel(normalizedPriceLevel: String): Boolean {
        val rulePriceLevel = normalize(priceLevel)

        /*
         * Blank price level means universal for that matching customer/item.
         * This is mainly for ALL CUSTOMERS rules.
         */
        if (rulePriceLevel.isBlank()) return true

        return rulePriceLevel == normalizedPriceLevel
    }

    private fun rule(
        customerId: String,
        itemId: String,
        qtyDiscountId: String,
        priceLevel: String,
        vararg breaks: QtyDiscountBreak
    ): QtyDiscountRule {
        return QtyDiscountRule(
            customerId = customerId,
            itemId = itemId,
            qtyDiscountId = qtyDiscountId,
            priceLevel = priceLevel.ifBlank { null },
            breaks = breaks.toList().sortedBy { it.minQty }
        )
    }

    private fun breakAt(minQty: Number, discountPercent: Double): QtyDiscountBreak {
        return QtyDiscountBreak(minQty.toDouble(), discountPercent)
    }

    fun defaultRules(): List<MasterQtyDiscountRule> {
        return rules.map { rule ->
            MasterQtyDiscountRule(
                customerId = rule.customerId,
                itemId = rule.itemId,
                qtyDiscountId = rule.qtyDiscountId,
                priceLevel = rule.priceLevel,
                breaks = rule.breaks.map { breakPoint ->
                    MasterQtyDiscountBreak(
                        minQty = breakPoint.minQty,
                        discountPercent = breakPoint.discountPercent
                    )
                }
            )
        }
    }

    private fun normalize(value: String?): String {
        return value
            ?.trim()
            ?.uppercase()
            ?.replace(Regex("""\s+"""), " ")
            .orEmpty()
    }

    private fun normalizeSku(value: String?): String {
        return value
            ?.trim()
            ?.uppercase()
            ?.replace(" ", "")
            ?.replace("_", "")
            .orEmpty()
    }

    private fun roundMoney(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    private const val ALL_CUSTOMERS = "ALL CUSTOMERS"

    private val rules = listOf(
        rule("ALL CUSTOMERS", "PH0015-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH0025-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH0060-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH0114-1B-100", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH0114-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH1013-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH1114-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH3060-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH4070-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH5090-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "PH7010-1B-50", "PH", "", breakAt(50, 0.030), breakAt(100, 0.050), breakAt(500, 0.100)),
        rule("ALL CUSTOMERS", "220-200-2070", "220-200-2070", "", breakAt(100, 0.250)),

        rule("AQUA RESEARCH", "AQR-CHL5-1B25", "AQR-CHL5-1B25", "DISTRIBUTOR", breakAt(2500, 0.050)),

        rule("BARTOVATION LLC", "145-500V-100", "145-500V-100", "DIST - 15%", breakAt(5, 0.020), breakAt(70, 0.075)),
        rule("BARTOVATION LLC", "158-500V-100", "158-500V-100", "DIST - 15%", breakAt(3, 0.020), breakAt(10, 0.050)),
        rule("BARTOVATION LLC", "165-500V-100", "165-500V-100", "DIST - 15%", breakAt(3, 0.020), breakAt(10, 0.050)),
        rule("BARTOVATION LLC", "166-500V-100", "166-500V-100", "DIST - 15%", breakAt(3, 0.020), breakAt(10, 0.050)),
        rule("BARTOVATION LLC", "196-500V-100", "195-500V-100", "DIST - 15%", breakAt(3, 0.020), breakAt(10, 0.050)),
        rule("BARTOVATION LLC", "AMM-100-1V-25", "AMM-100-1V-25", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "BARTOVATION QUIRES", "BART QUIRES", "DIST - 15%", breakAt(10, 0.100)),
        rule("BARTOVATION LLC", "CHL-10-1V-50", "CHL-10-1V-50", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "CHL-1000-1V-100", "CHL-1000-1V-100", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "CHL-10000-1V-100", "CHL-10000-1V-100", "DIST - 15%", breakAt(50, 0.150), breakAt(100, 0.200)),
        rule("BARTOVATION LLC", "CHL-2000-1V-100", "CHL-2000-1V-100", "DIST - 15%", breakAt(250, 0.050), breakAt(500, 0.100)),
        rule("BARTOVATION LLC", "CHL-D500-1V-50", "CHL-D500-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "CHLD-PH0245-1V-100", "CHLD-PH0245-1V-100", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "GLU-1B-100", "GLU-1B-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "GLU-1B-50", "GLU-1B-50", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "HARD-1V-100", "HARD-1V-100", "DIST - 15%", breakAt(100, 0.050), breakAt(250, 0.100)),
        rule("BARTOVATION LLC", "HARD-1V-50", "HARD-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "MOL-1V-25", "MOL-1V-25", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "MOL-PH5010-1V-25", "MOL-PH5010-1V-25", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "NAT-1V-25", "NAT-1V-25", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "NIT-NAT-1V-50", "NIT-NAT-1V-50", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "PAA-160-1V-100", "PAA-160-1V-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "PAA-500-1V-50", "PAA-500-1V-50", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "PER-100-1V-100", "PER-100-1V-100", "DIST - 15%", breakAt(250, 0.100), breakAt(500, 0.150)),
        rule("BARTOVATION LLC", "PER-100-1V-50", "PER-100-1V-50", "DIST - 15%", breakAt(250, 0.100), breakAt(500, 0.150)),
        rule("BARTOVATION LLC", "PAA-1000-1V-50", "PAA-1000-1V-50", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "PER-10000-1V-50", "PER-10000-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "PER-400-1V-100", "PER-400-1V-100", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "PH0114-1V-100", "PH0114-1V-100", "DIST - 15%", breakAt(250, 0.050), breakAt(500, 0.100)),
        rule("BARTOVATION LLC", "PH2844-1V-100", "PH2844-1V-100", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "PH4510-3-1V-50", "PH4510-3-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "PHO-1V-50", "PHO-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "QAC-400-1B-100", "QAC-400-1B-100", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "QAC-400-1V-100", "QAC-400-1V-100", "DIST - 15%", breakAt(100, 0.020), breakAt(250, 0.050), breakAt(500, 0.100)),
        rule("BARTOVATION LLC", "QAC-400-1V-50", "QAC-400-1V-50", "DIST - 15%", breakAt(250, 0.020)),
        rule("BARTOVATION LLC", "QAC-1500-1V-50", "QAC1500", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050), breakAt(5000, 0.090)),
        rule("BARTOVATION LLC", "SPC-CHL-200-1V-100", "SPC-CHL-200-1V-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "SPC-CHL-300-1V-100", "SPC-CHL-300-1V-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "SPC-CHL200-1V-100", "SPC-CHL200-1V-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "SPC-CHL300-1V-100", "SPC-CHL300-1V-100", "DIST - 15%", breakAt(250, 0.030), breakAt(500, 0.050)),
        rule("BARTOVATION LLC", "SUL-1V-50", "SUL-1V-50", "DIST - 15%", breakAt(250, 0.020)),

        rule("CHEM-SUPPLY", "NAT-1V-100", "NAT-1V-100", "DISTRIBUTOR", breakAt(500, 0.150)),
        rule("CHEM-SUPPLY", "PH0114-3-1V-100", "PH-0114-3-1V-100", "DIST + 50%", breakAt(250, 0.030)),

        rule("DEARDORFF FITZ", "CHL-10000-1V-50", "CHL100001V50", "DIST + 50%", breakAt(1000, 0.150)),
        rule("DEARDORFF FITZ", "CHL-4800-1V-50", "CHL48001V50", "DIST + 50%", breakAt(1000, 0.150)),

        rule("KROWNE METAL CORPORA", "145-500V-100", "145-500V-100", "DIST - 10%", breakAt(5, 0.020), breakAt(70, 0.075)),
        rule("KROWNE METAL CORPORA", "PH3060-1V-50", "PH30601V50", "DIST - 10%", breakAt(2000, 0.040)),
        rule("KROWNE METAL CORPORA", "QAC-1500-1V-50", "QAC1500", "DIST - 10%", breakAt(250, 0.030), breakAt(500, 0.050), breakAt(5000, 0.090)),

        rule("PHENOMUNE", "165-1B-1000", "165-1B-1000", "DIST + 100%", breakAt(100, 0.050), breakAt(300, 0.150), breakAt(500, 0.250)),
        rule("PHENOMUNE", "166-1B-1000", "166-1B-1000", "DIST + 100%", breakAt(100, 0.050), breakAt(300, 0.150), breakAt(500, 0.250)),
        rule("PHENOMUNE", "196-1B-1000", "196-1B-1000", "DIST + 100%", breakAt(100, 0.050), breakAt(300, 0.150), breakAt(500, 0.250)),
        rule("PHENOMUNE", "158-1B-1000", "158-1B-1000", "DIST + 100%", breakAt(100, 0.050), breakAt(300, 0.150), breakAt(500, 0.250)),

        rule("PINETREE INSTRUMENTS", "240-100-1622", "240-100-1622", "DIST - 10%", breakAt(5, 0.020)),
        rule("PINETREE INSTRUMENTS", "250-100-2024", "250-100-2024", "DIST - 10%", breakAt(5, 0.020)),
        rule("PINETREE INSTRUMENTS", "PER-HP-1V-50", "PER-HP-1V-50", "DIST - 10%", breakAt(2000, 0.200), breakAt(500, 0.103))
    )
}
