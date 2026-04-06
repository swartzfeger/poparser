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
                    AllPointsIlLayoutStrategy(),
                    AquaResearchLayoutStrategy(),
                    AutoChlorSystemTnLayoutStrategy(),
                    BaileysTestStripsLayoutStrategy(),
                    BartovationLayoutStrategy(),
                    CarolinaBiologicalLayoutStrategy(),
                    ChemSupplyLayoutStrategy(),
                    CovenantAviationSecurityLayoutStrategy(),
                    CovidienCtLayoutStrategy(),
                    DiversifiedFoodserviceLayoutStrategy(),
                    DiverseyTurkeyLayoutStrategy(),
                    DoveLayoutStrategy(),
                    DrakeLayoutStrategy(),
                    ElectronicControlsDesignLayoutStrategy(),
                    EcolabLayoutStrategy(),
                    EcolabPeruLayoutStrategy(),
                    EiscoSciLayoutStrategy(),
                    EtdDirectSupplyLayoutStrategy(),
                    FreseniusMedicalLayoutStrategy(),
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