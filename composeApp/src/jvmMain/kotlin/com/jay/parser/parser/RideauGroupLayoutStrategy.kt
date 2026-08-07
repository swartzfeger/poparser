package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class RideauGroupLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Rideau Group Inc."

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("OURGSTHSTNO104529599") &&
                text.contains("SUPPLIERPRECISIONLABORATORIESINC") &&
                text.contains("SHIPTORIDEAUSUPPLY")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("OURGSTHSTNO104529599")) score += 160
        if (text.contains("SHIPTORIDEAUSUPPLY")) score += 120
        if (text.contains("SUPPLIERPRECISIONLABORATORIESINC")) score += 80
        if (text.contains("YOURPARTNO")) score += 60
        if (text.contains("PRELAB")) score += 40
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map(::cleanLine)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "RIDEAU GROUP INC.",
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "Rideau Supply",
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.country,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.postalCode,
            terms = clean.firstOrNull { compact(it).contains("NET30DAYS") }
                ?.let { "Net 30 Days" },
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (skuIndex in lines.indices) {
            val skuMatch = YOUR_PART_PATTERN.find(lines[skuIndex]) ?: continue
            val sku = skuMatch.groupValues[1].uppercase()
            val rowIndex = ((skuIndex - 1) downTo maxOf(0, skuIndex - 5))
                .firstOrNull { ITEM_ROW_PATTERN.matches(lines[it]) }
                ?: continue
            val row = ITEM_ROW_PATTERN.find(lines[rowIndex]) ?: continue
            val quantity = row.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val unitPrice = row.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue

            val poDescription = lines.subList(rowIndex + 1, skuIndex)
                .joinToString(" ")
                .trim()

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank {
                        poDescription.ifBlank { sku }
                    },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun parseShipTo(lines: List<String>): AddressParts {
        val headerIndex = lines.indexOfFirst {
            compact(it).contains("SUPPLIERPRECISIONLABORATORIESINC") &&
                    compact(it).contains("SHIPTORIDEAUSUPPLY")
        }
        if (headerIndex == -1) return AddressParts()

        val streetRaw = lines.getOrNull(headerIndex + 1)
            ?.let { removeCompactPrefix(it, "415AIRPARKDRIVE") }
            .orEmpty()
        val cityStateRaw = lines.getOrNull(headerIndex + 2)
            ?.let { removeCompactPrefix(it, "COTTONWOOD,ARIZONA") }
            .orEmpty()
        val postalRaw = lines.getOrNull(headerIndex + 3)
            ?.let { removeCompactPrefix(it, "86326") }
            .orEmpty()

        val cityState = cityStateRaw.split(",", limit = 2)
        val city = cityState.getOrNull(0)?.let(::humanizeWords)?.ifBlank { null }
        val regionRaw = cityState.getOrNull(1).orEmpty()
        val region = normalizeRegion(regionRaw)
        val compactPostal = postalRaw.uppercase().replace(Regex("""\s+"""), "")
        val isCanadian = CANADIAN_POSTAL_PATTERN.matches(compactPostal)
        val postalCode = when {
            isCanadian -> "${compactPostal.take(3)} ${compactPostal.drop(3)}"
            US_ZIP_PATTERN.matches(compactPostal) -> compactPostal
            else -> postalRaw.ifBlank { null }
        }

        return AddressParts(
            addressLine1 = humanizeWords(streetRaw).ifBlank { null },
            city = city,
            state = region,
            postalCode = postalCode,
            country = if (isCanadian) "Canada" else null
        )
    }

    private fun removeCompactPrefix(value: String, prefix: String): String {
        val compactValue = value.replace(Regex("""\s+"""), "")
        return if (compactValue.startsWith(prefix, ignoreCase = true)) {
            compactValue.substring(prefix.length)
        } else {
            ""
        }
    }

    private fun humanizeWords(value: String): String = value
        .replace(Regex("""(?<=\d)(?=[A-Za-z])"""), " ")
        .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun normalizeRegion(value: String): String? {
        val normalized = value.uppercase().replace(Regex("""[^A-Z]"""), "")
        if (normalized.length == 2) return normalized
        return REGION_CODES[normalized] ?: humanizeWords(value).ifBlank { null }
    }

    private fun cleanLine(value: String): String = value.replace(Regex("""\s+"""), " ").trim()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class AddressParts(
        val addressLine1: String? = null,
        val city: String? = null,
        val state: String? = null,
        val postalCode: String? = null,
        val country: String? = null
    )

    private companion object {
        val ORDER_NUMBER_PATTERN = Regex("""\b([A-Z]{2}-\d{8})\b""")
        val ITEM_ROW_PATTERN = Regex(
            """^([\d,]+(?:\.\d+)?)\s+[A-Z]+\s+\S+\s+\d{2}-[A-Z]{3}-\d{4}\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
        val YOUR_PART_PATTERN = Regex(
            """YOUR\s+PART\s+NO:\s*([A-Z0-9]+(?:-[A-Z0-9]+)+)""",
            RegexOption.IGNORE_CASE
        )
        val CANADIAN_POSTAL_PATTERN = Regex("""^[A-Z]\d[A-Z]\d[A-Z]\d$""")
        val US_ZIP_PATTERN = Regex("""^\d{5}(?:-\d{4})?$""")
        val REGION_CODES = mapOf(
            "ALBERTA" to "AB", "BRITISHCOLUMBIA" to "BC", "MANITOBA" to "MB",
            "NEWBRUNSWICK" to "NB", "NEWFOUNDLANDANDLABRADOR" to "NL",
            "NORTHWESTTERRITORIES" to "NT", "NOVASCOTIA" to "NS", "NUNAVUT" to "NU",
            "ONTARIO" to "ON", "PRINCEEDWARDISLAND" to "PE", "QUEBEC" to "QC",
            "SASKATCHEWAN" to "SK", "YUKON" to "YT",
            "ALABAMA" to "AL", "ALASKA" to "AK", "ARIZONA" to "AZ", "ARKANSAS" to "AR",
            "CALIFORNIA" to "CA", "COLORADO" to "CO", "CONNECTICUT" to "CT",
            "DELAWARE" to "DE", "DISTRICTOFCOLUMBIA" to "DC", "FLORIDA" to "FL",
            "GEORGIA" to "GA", "HAWAII" to "HI", "IDAHO" to "ID", "ILLINOIS" to "IL",
            "INDIANA" to "IN", "IOWA" to "IA", "KANSAS" to "KS", "KENTUCKY" to "KY",
            "LOUISIANA" to "LA", "MAINE" to "ME", "MARYLAND" to "MD",
            "MASSACHUSETTS" to "MA", "MICHIGAN" to "MI", "MINNESOTA" to "MN",
            "MISSISSIPPI" to "MS", "MISSOURI" to "MO", "MONTANA" to "MT",
            "NEBRASKA" to "NE", "NEVADA" to "NV", "NEWHAMPSHIRE" to "NH",
            "NEWJERSEY" to "NJ", "NEWMEXICO" to "NM", "NEWYORK" to "NY",
            "NORTHCAROLINA" to "NC", "NORTHDAKOTA" to "ND", "OHIO" to "OH",
            "OKLAHOMA" to "OK", "OREGON" to "OR", "PENNSYLVANIA" to "PA",
            "RHODEISLAND" to "RI", "SOUTHCAROLINA" to "SC", "SOUTHDAKOTA" to "SD",
            "TENNESSEE" to "TN", "TEXAS" to "TX", "UTAH" to "UT", "VERMONT" to "VT",
            "VIRGINIA" to "VA", "WASHINGTON" to "WA", "WESTVIRGINIA" to "WV",
            "WISCONSIN" to "WI", "WYOMING" to "WY"
        )
    }
}
