package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MirOilLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "MirOil USA"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()

        return text.contains("MIROIL") &&
                text.contains("PURCHASE ORDER") &&
                text.contains("PRECISION LABORATORIES")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0

        if (text.contains("MIROIL USA")) score += 120
        if (text.contains("MIROIL USA, LLC")) score += 120
        if (text.contains("PURCHASE ORDER")) score += 80
        if (text.contains("PRECISION LABORATORIES")) score += 60
        if (Regex("""\bPOM-\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) score += 80
        if (text.contains("PRECLAB-145")) score += 50
        if (text.contains("145-500")) score += 50
        if (text.contains("BLEINHEIM")) score += 40
        if (text.contains("ORDERS@MIROIL.COM")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        val shipTo = findShipTo(textLines)

        return ParsedPdfFields(
            customerName = "MIROIL USA, LLC",
            orderNumber = findOrderNumber(textLines),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = findTerms(textLines),
            items = findItems(textLines)
        )
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private fun findOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""Purchase\s+Order\s+No\s*:\s*(POM-\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return it }

        Regex("""\b(POM-\d+)\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.uppercase()
            ?.let { return it }

        return null
    }

    private fun findTerms(lines: List<String>): String? {
        val joined = lines.joinToString("\n")

        Regex("""Terms\s*:\s*(Net\s*\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.let { return it }

        Regex("""\b(Net\s*\d+)\b""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.let { return it }

        return null
    }

    private fun findShipTo(lines: List<String>): ShipToBlock {
        findNonMirOilShipTo(lines)?.let { return it }

        val joined = lines.joinToString("\n")

        val addressLine = lines
            .firstOrNull {
                it.contains("Bleinheim", ignoreCase = true) &&
                        !it.trim().startsWith("Address:", ignoreCase = true)
            }
            ?.let { extractRightSideAddress(it) }
            ?: "13114 Bleinheim Lane"

        val cityLine = lines
            .firstOrNull {
                it.contains("Matthews", ignoreCase = true) &&
                        (
                                it.contains("North Carolina", ignoreCase = true) ||
                                        Regex("""\bNC\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
                                ) &&
                        Regex("""\b\d{5}\b""").containsMatchIn(it)
            }
            ?.let { extractRightSideCityStateZip(it) }
            ?: "Matthews North Carolina 28105"

        val cityStateZip = parseCityStateZip(cityLine)

        val hasMirOilShipTo = joined.contains("Ship To", ignoreCase = true) ||
                joined.contains("MirOil USA, LLC", ignoreCase = true)

        return ShipToBlock(
            shipToCustomer = if (hasMirOilShipTo) "MirOil USA, LLC" else null,
            addressLine1 = addressLine,
            addressLine2 = null,
            city = cityStateZip.city,
            state = cityStateZip.state,
            zip = cityStateZip.zip
        )
    }

    private fun findNonMirOilShipTo(lines: List<String>): ShipToBlock? {
        val nameLine = lines.firstOrNull {
            it.contains("Precision Laboratories, Inc.", ignoreCase = true)
        } ?: return null

        val shipToName = nameLine
            .substringAfter("Precision Laboratories, Inc.", missingDelimiterValue = "")
            .trim()
        if (shipToName.isBlank() || shipToName.contains("MirOil", ignoreCase = true)) return null

        val nameContinuation = lines
            .firstOrNull { it.startsWith("415 Airpark Road", ignoreCase = true) }
            ?.substringAfter("415 Airpark Road", missingDelimiterValue = "")
            ?.substringBefore("Purchase Invoice No:", missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.containsPhoneOrEmail() }

        val address = lines
            .firstOrNull { it.contains("orders@preclaboratories.co", ignoreCase = true) }
            ?.substringAfter("orders@preclaboratories.co", missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { STREET_ADDRESS_PATTERN.containsMatchIn(it) }

        val cityMatch = lines
            .asSequence()
            .map { it.replace(Regex("""^m\s+""", RegexOption.IGNORE_CASE), "").trim() }
            .filterNot { it.contains("Cottonwood", ignoreCase = true) }
            .mapNotNull { CITY_STATE_ZIP_PATTERN.find(it) }
            .firstOrNull()

        return ShipToBlock(
            shipToCustomer = listOfNotNull(shipToName, nameContinuation).joinToString(" "),
            addressLine1 = address,
            addressLine2 = null,
            city = cityMatch?.groupValues?.getOrNull(1)?.trim()?.uppercase(),
            state = cityMatch?.groupValues?.getOrNull(2)?.uppercase(),
            zip = cityMatch?.groupValues?.getOrNull(3)
        )
    }

    private fun String.containsPhoneOrEmail(): Boolean =
        contains("@") || Regex("""\d{3}\s+\d{3}\s+\d{4}""").containsMatchIn(this)

    private data class CityStateZip(
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private fun extractRightSideAddress(line: String): String {
        val cleaned = line
            .replace(Regex("""\s+"""), " ")
            .trim()

        Regex("""\d{3}[-\s]?\d{3}[-\s]?\d{4}\s+(.+Bleinheim\s+(?:Lane|Ln))""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return normalizeAddress(it) }

        Regex("""orders@preclaboratories\.co\s+(.+Bleinheim\s+(?:Lane|Ln))""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return normalizeAddress(it) }

        Regex("""(13114\s+Bleinheim\s+(?:Lane|Ln))""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return normalizeAddress(it) }

        return normalizeAddress(cleaned)
    }

    private fun extractRightSideCityStateZip(line: String): String {
        val cleaned = line
            .replace(Regex("""\s+"""), " ")
            .trim()

        Regex("""orders@preclaboratories\.co\s+(.+?\s+(?:North Carolina|NC)\s+\d{5})""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return normalizeCityStateZipText(it) }

        Regex("""(Matthews\s+(?:North Carolina|NC)\s+\d{5})""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return normalizeCityStateZipText(it) }

        return normalizeCityStateZipText(cleaned)
    }

    private fun normalizeAddress(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .replace(" Ln", " Lane", ignoreCase = true)
            .trim()
    }

    private fun normalizeCityStateZipText(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .replace("North Carolina", "NC", ignoreCase = true)
            .trim()
    }

    private fun parseCityStateZip(value: String): CityStateZip {
        val cleaned = normalizeCityStateZipText(value)

        val match = Regex(
            """^(.*?)\s+(NC|North Carolina)\s+(\d{5})$""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)

        return if (match != null) {
            CityStateZip(
                city = match.groupValues[1].trim().uppercase(),
                state = "NC",
                zip = match.groupValues[3].trim()
            )
        } else {
            CityStateZip(null, null, null)
        }
    }

    private fun findItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = cleanItemLine(lines[i])
            val next = cleanItemLine(lines.getOrNull(i + 1).orEmpty())
            val next2 = cleanItemLine(lines.getOrNull(i + 2).orEmpty())

            val sku = findSupplierSku(line, next, next2) ?: continue
            val amounts = findItemAmounts(line, next, next2) ?: continue

            val key = "$sku|${amounts.totalEachQuantity}|${amounts.casePrice}"
            if (!seen.add(key)) continue

            items += ParsedPdfItem(
                sku = sku,
                description = null,
                quantity = amounts.totalEachQuantity,
                unitPrice = amounts.casePrice
            )
        }

        return items
    }

    private fun findSupplierSku(line: String, next: String, next2: String): String? {
        val window = "$line $next $next2"
            .uppercase()
            .replace('\uFFFE', '-')
            .replace("–", "-")
            .replace("—", "-")
            .replace(Regex("""\s+"""), " ")

        return when {
            window.contains("106-QR5-") && window.contains("500V-100") -> "106-QR5-500V-100"
            window.contains("CHLORINE") && window.contains("145-500") -> "145-500V-100"
            else -> null
        }
    }

    private data class ItemAmounts(
        val totalEachQuantity: Double,
        val casePrice: Double
    )

    private fun findItemAmounts(line: String, next: String, next2: String): ItemAmounts? {
        val window = "$line $next $next2"
            .replace(",", "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val match = ITEM_AMOUNTS_PATTERN.find(window) ?: return null
        val casePrice = match.groupValues[2].toDoubleOrNull() ?: return null
        val totalEachQuantity = match.groupValues[4].toDoubleOrNull() ?: return null

        return ItemAmounts(
            totalEachQuantity = totalEachQuantity,
            casePrice = casePrice
        )
    }

    private fun cleanItemLine(value: String): String {
        return value
            .replace('\uFFFE', '-')
            .replace("–", "-")
            .replace("—", "-")
            .replace("S$", "$")
            .replace("§", "$")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private companion object {
        val STREET_ADDRESS_PATTERN = Regex("""^\d+\s+.+""")
        val CITY_STATE_ZIP_PATTERN = Regex(
            """^(.+?)\s+([A-Z]{2})\s+(\d{5})(?:-\d{4})?(?:\s+United States)?$""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_AMOUNTS_PATTERN = Regex(
            """\b500\s+(\d+(?:\.\d+)?)\s+\$?\s*(\d+(?:\.\d+)?)\s+\$?\s*(\d+(?:\.\d+)?)\s+(\d+)\s+\$?\s*(\d+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
    }
}
