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
                    CharlotteProductsLayoutStrategy(),
                    ChemSupplyLayoutStrategy(),
                    ChosunMeasurementLayoutStrategy(),
                    CovenantAviationLayoutStrategy(),
                    CovidienCtLayoutStrategy(),
                    DiversifiedFoodserviceLayoutStrategy(),
                    DiverseyTurkeyLayoutStrategy(),
                    DoveLayoutStrategy(),
                    DrakeLayoutStrategy(),
                    EcaEducationalServicesLayoutStrategy(),
                    EcolabLayoutStrategy(),
                    EcolabPeruLayoutStrategy(),
                    EiscoSciLayoutStrategy(),
                    ElectronicControlsDesignLayoutStrategy(),
                    EtdDirectSupplyLayoutStrategy(),
                    FisherScientificCoLayoutStrategy(),
                    FlinnScientificLayoutStrategy(),
                    FreseniusMedicalLayoutStrategy(),
                    HomeScienceToolsLayoutStrategy(),
                    IndustriasCorySasLayoutStrategy(),
                    InterconChemicalLayoutStrategy(),
                    JayhawkSalesTxLayoutStrategy(),
                    JayhawkSalesWiLayoutStrategy(),
                    KrowneLayoutStrategy(),
                    MedlineLayoutStrategy(),
                    MirOilLayoutStrategy(),
                    MoreFlavorLayoutStrategy(),
                    NalcoCompanyLayoutStrategy(),
                    NationalChemicalsLayoutStrategy(),
                    PdqManufacturingLayoutStrategy(),
                    PinetreeInstrumentsLayoutStrategy(),
                    PlantProductsLayoutStrategy(),
                    PrecisionEuropeLayoutStrategy(),
                    ProlabScientificLayoutStrategy(),
                    QualityScienceLabsLayoutStrategy(),
                    SanitechLayoutStrategy(),
                    SchoolSpecialtyLayoutStrategy(),
                    ScienceFirstLayoutStrategy(),
                    SensonicsIntlLayoutStrategy(),
                    TcdPartsLayoutStrategy(),
                    TsaInvoiceLayoutStrategy(),
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