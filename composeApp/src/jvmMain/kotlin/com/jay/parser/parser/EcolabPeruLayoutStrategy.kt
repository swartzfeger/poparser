package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class EcolabPeruLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "ECOLAB PERU"

    override fun matches(lines: List<String>): Boolean {
        val joined = compact(lines)

        return joined.contains("ECOLABPERUHOLDINGSS.R.L.") &&
                joined.contains("IMPORTORDERNO.20250296")
                || (
                joined.contains("ECOLABPERUHOLDINGS") &&
                        joined.contains("IMPORTORDERNO.") &&
                        joined.contains("REQUESTEDTO:SHIPTO:")
                )
    }

    override fun score(lines: List<String>): Int {
        val joined = compact(lines)
        var score = 0

        if (joined.contains("ECOLABPERUHOLDINGS")) score += 80
        if (joined.contains("IMPORTORDERNO.")) score += 40
        if (joined.contains("REQUESTEDTO:SHIPTO:")) score += 40
        if (joined.contains("LIMA-PERÚ") || joined.contains("LIMA-PERU")) score += 20
        if (joined.contains("QAC-400-100")) score += 20
        if (joined.contains("CHL-300-100")) score += 20

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)
        val shipTo = parseShipTo(textLines)

        return ParsedPdfFields(
            customerName = parseCustomerName(textLines),
            orderNumber = parseOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = parseItems(textLines)
        )
    }

    private fun parseCustomerName(lines: List<String>): String {
        val raw = lines.firstOrNull { compact(it).contains("ECOLABPERUHOLDINGSS.R.L.") }
            ?: return "ECOLAB PERU HOLDINGS S.R.L."

        return if (raw.contains(" ")) {
            raw.trim()
        } else {
            "ECOLAB PERU HOLDINGS S.R.L."
        }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val compact = compact(line)

            val match = Regex("""IMPORTORDERNO\.?([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
                .find(compact)

            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): InterpretedShipTo {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        for (line in lines) {
            val compact = compact(line)

            if (shipToCustomer == null && compact.contains("ECOLABPERÚHOLDINGSS.R.L.")) {
                shipToCustomer = "Ecolab Perú Holdings S.R.L."
            } else if (shipToCustomer == null && compact.contains("ECOLABPERUHOLDINGSS.R.L.")) {
                shipToCustomer = "Ecolab Peru Holdings S.R.L."
            }

            if (addressLine1 == null && compact.contains("AV.LAESTANCIAMZ.QLT21,22,23,24")) {
                addressLine1 = "AV. LA ESTANCIA MZ. Q LT 21,22,23,24"
            }

            if (addressLine2 == null && compact.contains("URBINDUSTRIALELLÚCUMOLURÍN")) {
                addressLine2 = "URB Industrial El Lúcumo Lurín"
            } else if (addressLine2 == null && compact.contains("URBINDUSTRIALELLUCUMOLURIN")) {
                addressLine2 = "URB Industrial El Lucumo Lurin"
            }

            if (compact.contains("LIMA-PERÚ")) {
                city = "Lima"
                state = "Perú"
            } else if (compact.contains("LIMA-PERU")) {
                city = "Lima"
                state = "Peru"
            }
        }

        if (shipToCustomer.isNullOrBlank()) {
            shipToCustomer = "Ecolab Perú Holdings S.R.L."
        }

        return InterpretedShipTo(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>) =
        buildList {
            val rowRegex = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+(EA|PACK|CASE|BOX|BAG|VIAL)\s+(\d+(?:,\d{3})*(?:\.\d+)?)\s+\$\s*([\d,]+\.\d{2})\s+\$\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            )

            for (line in lines) {
                val trimmed = line.replace(Regex("""\s+"""), " ").trim()
                val compact = compact(line)

                if (!(compact.startsWith("QAC-400-100") || compact.startsWith("CHL-300-100"))) {
                    continue
                }

                val match = rowRegex.find(trimmed)
                if (match != null) {
                    val rawSku = match.groupValues[1].trim()
                    val poDescription = match.groupValues[2].trim()
                    val sku = resolvePeruSku(rawSku, poDescription)

                    val quantity = match.groupValues[4].replace(",", "").toDoubleOrNull()
                    val unitPrice = match.groupValues[5].replace(",", "").toDoubleOrNull()

                    if (quantity != null && unitPrice != null) {
                        val finalDescription = ItemMapper.getItemDescription(sku).ifBlank {
                            poDescription.ifBlank { sku }
                        }

                        add(
                            item(
                                sku = sku,
                                description = finalDescription,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                    continue
                }

                val compactMatch = Regex(
                    """^([A-Z0-9-]+)(.+?)(EA|PACK|CASE|BOX|BAG|VIAL)(\d+(?:\.\d+)?)\$([\d,]+\.\d{2})\$([\d,]+\.\d{2})$""",
                    RegexOption.IGNORE_CASE
                ).find(compact)

                if (compactMatch != null) {
                    val rawSku = compactMatch.groupValues[1].trim()
                    val compactDescription = compactMatch.groupValues[2].trim()
                    val sku = resolvePeruSku(rawSku, compactDescription)

                    val quantity = compactMatch.groupValues[4].replace(",", "").toDoubleOrNull()
                    val unitPrice = compactMatch.groupValues[5].replace(",", "").toDoubleOrNull()

                    if (quantity != null && unitPrice != null) {
                        val finalDescription = ItemMapper.getItemDescription(sku).ifBlank { sku }

                        add(
                            item(
                                sku = sku,
                                description = finalDescription,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }
                }
            }
        }

    private fun resolvePeruSku(rawSku: String, poDescription: String): String {
        val normalizedRaw = normalizeSku(rawSku)
        val desc = poDescription.uppercase()

        return when (normalizedRaw) {
            "QAC-400-100" -> {
                when {
                    desc.contains("VIAL") -> "QAC-400-1V-100"
                    desc.contains("BAG") -> "QAC-400-1B-100"
                    else -> "QAC-400-1V-100" // safest default for this PDF
                }
            }

            "CHL-300-100" -> {
                when {
                    desc.contains("VIAL") -> "CHL-300-1V-100"
                    else -> "CHL-300-1V-100" // only logical catalog match
                }
            }

            else -> normalizedRaw
        }
    }

    private fun compact(lines: List<String>): String =
        lines.joinToString("\n") { compact(it) }

    private fun compact(value: String): String =
        value.uppercase()
            .replace(Regex("""\s+"""), "")
}