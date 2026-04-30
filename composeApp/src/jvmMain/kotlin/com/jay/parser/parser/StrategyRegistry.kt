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
                    AquaPhoenixScientificLayoutStrategy(),
                    AquaResearchLayoutStrategy(),
                    AutoChlorSystemTnLayoutStrategy(),
                    BaileysTestStripsLayoutStrategy(),
                    BartovationLayoutStrategy(),
                    CarolinaBiologicalLayoutStrategy(),
                    ChemSupplyLayoutStrategy(),
                    CovenantAviationLayoutStrategy(),
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
                    FisherScientificCoLayoutStrategy(),
                    FlinnScientificLayoutStrategy(),
                    FreseniusMedicalLayoutStrategy(),
                    HomeScienceToolsLayoutStrategy(),
                    InterconChemicalLayoutStrategy(),
                    JayhawkSalesTxLayoutStrategy(),
                    JayhawkSalesWiLayoutStrategy(),
                    KrowneLayoutStrategy(),
                    MedlineLayoutStrategy(),
                    MoreFlavorLayoutStrategy(),
                    NalcoCompanyLayoutStrategy(),
                    NationalChemicalsLayoutStrategy(),
                    PdqManufacturingLayoutStrategy(),
                    PinetreeInstrumentsLayoutStrategy(),
                    PlantProductsLayoutStrategy(),
                    PrecisionEuropeLayoutStrategy(),
                    QualityScienceLabsLayoutStrategy(),
                    SanitechLayoutStrategy(),
                    SchoolSpecialtyLayoutStrategy(),
                    ScienceFirstLayoutStrategy(),
                    SensonicsIntlLayoutStrategy(),
                    TcdPartsLayoutStrategy(),
                    UnipakLayoutStrategy(),
                    UsaBlueBookLayoutStrategy(),
                    VikingPureLayoutStrategy(),
                    VwrLayoutStrategy(),
                    WebbChemicalAndPaperLayoutStrategy()

                )
            )
        }
    }
}