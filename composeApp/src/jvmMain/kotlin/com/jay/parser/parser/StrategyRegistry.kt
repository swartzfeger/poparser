package com.jay.parser.parser

class StrategyRegistry(
    private val strategies: List<LayoutStrategy>
) {
    fun choose(lines: List<String>): LayoutStrategy? {
        return strategies
            .filter { it.matches(lines) }
            .maxByOrNull { it.score(lines) }
    }

    companion object {
        fun default(): StrategyRegistry {
            return StrategyRegistry(
                listOf(
                    AutoChlorSystemTnLayoutStrategy(),
                    BartovationLayoutStrategy(),
                    CarolinaBiologicalLayoutStrategy(),
                    DiversifiedFoodserviceLayoutStrategy(),
                    DrakeLayoutStrategy(),
                    EiscoSciLayoutStrategy(),
                    KrowneLayoutStrategy(),
                    MedlineLayoutStrategy(),
                    PinetreeLayoutStrategy(),
                    TcdPartsLayoutStrategy(),
                    VwrLayoutStrategy(),
                    WebbChemicalAndPaperLayoutStrategy()

                )
            )
        }
    }
}