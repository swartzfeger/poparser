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
                    AquaResearchLayoutStrategy(),
                    AutoChlorSystemTnLayoutStrategy(),
                    BaileysTestStripsLayoutStrategy(),
                    BartovationLayoutStrategy(),
                    CarolinaBiologicalLayoutStrategy(),
                    CovenantAviationSecurityLayoutStrategy(),
                    CovidienCtLayoutStrategy(),
                    DiversifiedFoodserviceLayoutStrategy(),
                    DiverseyTurkeyLayoutStrategy(),
                    DoveLayoutStrategy(),
                    DrakeLayoutStrategy(),
                    ElectronicControlsDesignLayoutStrategy(),
                    EcolabLayoutStrategy(),
                    EiscoSciLayoutStrategy(),
                    EtdDirectSupplyLayoutStrategy(),
                    KrowneLayoutStrategy(),
                    MedlineLayoutStrategy(),
                    NalcoCompanyLayoutStrategy(),
                    NationalChemicalsLayoutStrategy(),
                    PinetreeInstrumentsLayoutStrategy(),
                    TcdPartsLayoutStrategy(),
                    VwrLayoutStrategy(),
                    WebbChemicalAndPaperLayoutStrategy()

                )
            )
        }
    }
}