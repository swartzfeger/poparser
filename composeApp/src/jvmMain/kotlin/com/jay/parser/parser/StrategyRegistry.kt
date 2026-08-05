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
                    AdvanceProductsLayoutStrategy(),
                    AldonGatewayLayoutStrategy(),
                    AllPointsIlLayoutStrategy(),
                    AquaPhoenixScientificLayoutStrategy(),
                    AquaResearchLayoutStrategy(),
                    AutoChlorSystemTnLayoutStrategy(),
                    BaileysTestStripsLayoutStrategy(),
                    BartovationLayoutStrategy(),
                    BetaProcesosLayoutStrategy(),
                    ButlerChemicalLayoutStrategy(),
                    CarolinaBiologicalLayoutStrategy(),
                    CharlotteProductsLayoutStrategy(),
                    ChemSupplyLayoutStrategy(),
                    ChosunMeasurementLayoutStrategy(),
                    CmRepresentacionesLayoutStrategy(),
                    CovenantAviationLayoutStrategy(),
                    CovidienCtLayoutStrategy(),
                    DiversifiedFoodserviceLayoutStrategy(),
                    DiverseyTurkeyLayoutStrategy(),
                    DevereLayoutStrategy(),
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
