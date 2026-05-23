package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class MoreFlavorLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "MORE FLAVOR"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("MOREFLAVOR") ||
                text.contains("PURCHASING@MOREFLAVOR.COM") ||
                text.contains("16335JOHNGLENNPARKWAY") ||
                text.contains("ACCOUNT#:PRELAB") ||
                text.contains("PONUM:") && text.contains("PRELAB")
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("MOREFLAVOR")) score += 120
        if (text.contains("PURCHASING@MOREFLAVOR.COM")) score += 80
        if (text.contains("16335JOHNGLENNPARKWAY")) score += 80
        if (text.contains("ACCOUNT#:PRELAB")) score += 70
        if (text.contains("VNDR-1%10NET30")) score += 50
        if (text.contains("PONUM:")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)
        val orderNumber = parseOrderNumber(clean)

        return ParsedPdfFields(
            customerName = "MORE FLAVOR",
            orderNumber = orderNumber,
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")

        Regex("""PO\s*Num:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues?.get(1)
            ?.let { return it }

        Regex("""Purchase\s+Order:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues?.get(1)
            ?.let { return it }

        Regex("""PO\s*#\s*:?[\sA-Z]*?(\d{6})""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues?.get(1)
            ?.let { return it }

        return null
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""Vndr\s*-\s*1%\s*10\s*Net\s*30""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.value
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { normalize(it).contains("SHIPPINGADDRESS:") }
        if (idx >= 0) {
            val window = lines.drop(idx).take(12).joinToString(" ")
            val normalized = normalize(window)

            if (normalized.contains("16335JOHNGLENN") || normalized.contains("NEWCENTURY,KS66031")) {
                return defaultShipTo()
            }

            val company = lines.getOrNull(idx + 1)?.trim()
            val addr1 = lines.getOrNull(idx + 2)?.trim()
            val addr2 = lines.getOrNull(idx + 3)?.trim()
            val cityStateZipLine = lines.getOrNull(idx + 4)?.trim()
            val csz = parseCityStateZip(cityStateZipLine)

            if (company != null && addr1 != null && csz != null) {
                return ShipToBlock(
                    shipToCustomer = pretty(company),
                    addressLine1 = pretty(addr1),
                    addressLine2 = pretty(addr2),
                    city = csz.city,
                    state = csz.state,
                    zip = csz.zip
                )
            }
        }

        return defaultShipTo()
    }

    private fun defaultShipTo(): ShipToBlock {
        return ShipToBlock(
            shipToCustomer = "MoreFlavor, Inc.",
            addressLine1 = "16335 John Glenn Parkway",
            addressLine2 = "Suite 300",
            city = "New Century",
            state = "KS",
            zip = "66031"
        )
    }

    private fun parseCityStateZip(line: String?): CityStateZip? {
        if (line.isNullOrBlank()) return null

        val normalized = line
            .replace("NewCentury", "New Century")
            .replace("New Century,KS", "New Century, KS")
            .trim()

        val match = Regex("""^(.+?),\s*([A-Z]{2})\s+(\d{5}(?:-\d{4})?)$""", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?: return null

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim().uppercase(),
            zip = match.groupValues[3].trim()
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        parseDirectItemRows(lines, items, seen)
        parseDescriptionAnchoredRows(lines, items, seen)
        return items
    }

    private fun parseDirectItemRows(
        lines: List<String>,
        items: MutableList<ParsedPdfItem>,
        seen: MutableSet<String>
    ) {
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            val match = Regex(
                """^([A-Z0-9-]+)\s+(.+?)\s+([\d.]+)\s+\$([\d.]+)\s+\$([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            val rawSku = match?.groupValues?.getOrNull(1)?.trim()
            val sku = rawSku?.let { normalizeMoreFlavorSku(it, line) }

            if (match != null && sku != null && looksLikeSku(sku)) {
                val firstDesc = match.groupValues[2].trim()
                val parsedQuantity = match.groupValues[3].toDoubleOrNull()
                val unitPrice = match.groupValues[4].toDoubleOrNull()
                val extendedTotal = match.groupValues[5].replace(",", "").toDoubleOrNull()
                val quantity = if (unitPrice != null && extendedTotal != null) {
                    calculateQuantityFromTotal(extendedTotal, unitPrice) ?: parsedQuantity
                } else {
                    parsedQuantity
                }

                if (quantity != null && unitPrice != null) {
                    val descParts = mutableListOf(firstDesc)
                    var j = i + 1

                    while (j < lines.size) {
                        val next = lines[j].trim()
                        if (next.isBlank()) {
                            j++
                            continue
                        }

                        if (looksLikeFooter(next) || looksLikeItemStart(next) || resolveSkuFromLine(next) != null) break

                        if (next.equals(sku, ignoreCase = true) || next.startsWith("$sku ", ignoreCase = true)) {
                            j++
                            continue
                        }

                        if (looksLikePerishableNote(next)) {
                            j++
                            continue
                        }

                        descParts.add(next)
                        j++
                    }

                    addMoreFlavorItem(
                        items = items,
                        seen = seen,
                        sku = sku,
                        fallbackDescription = descParts.joinToString(" "),
                        quantity = quantity,
                        unitPrice = unitPrice
                    )

                    i = j
                    continue
                }
            }

            i++
        }
    }

    private fun parseDescriptionAnchoredRows(
        lines: List<String>,
        items: MutableList<ParsedPdfItem>,
        seen: MutableSet<String>
    ) {
        for (line in lines) {
            val sku = resolveSkuFromLine(line) ?: continue
            val moneyValues = extractMoneyValues(line)

            if (moneyValues.size < 2) continue

            val unitPrice = moneyValues[moneyValues.size - 2]
            val extendedTotal = moneyValues.last()
            val quantity = calculateQuantityFromTotal(extendedTotal, unitPrice)
                ?: extractQuantityBeforeUnitPrice(line, unitPrice)
                ?: continue

            addMoreFlavorItem(
                items = items,
                seen = seen,
                sku = sku,
                fallbackDescription = descriptionFromLine(line, sku),
                quantity = quantity,
                unitPrice = unitPrice
            )
        }
    }


    private fun extractMoneyValues(line: String): List<Double> {
        return Regex("""\$[\s_]*[\d_,]+(?:[._]\d{2})?""")
            .findAll(line)
            .mapNotNull { match ->
                match.value
                    .replace("$", "")
                    .replace("_", "")
                    .replace(",", "")
                    .replace(" ", "")
                    .trim()
                    .toDoubleOrNull()
            }
            .toList()
    }

    private fun calculateQuantityFromTotal(total: Double, unitPrice: Double): Double? {
        if (unitPrice <= 0.0 || total <= 0.0) return null

        val calculated = total / unitPrice
        val rounded = kotlin.math.round(calculated)

        return if (rounded in 1.0..10000.0 && kotlin.math.abs(calculated - rounded) <= 0.05) {
            rounded
        } else {
            null
        }
    }

    private fun extractQuantityBeforeUnitPrice(line: String, unitPrice: Double): Double? {
        val firstDollarIndex = line.indexOf('$')
        if (firstDollarIndex <= 0) return null

        val beforeMoney = line.substring(0, firstDollarIndex)
            .replace("_", " ")
            .replace("|", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val numericValues = Regex("""\d+(?:\.\d+)?""")
            .findAll(beforeMoney)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()

        if (numericValues.isEmpty()) return null

        val plausible = numericValues
            .filter { it in 1.0..10000.0 }
            .filterNot { it == unitPrice }

        return plausible.lastOrNull()
    }

    private fun resolveSkuFromLine(line: String): String? {
        val upper = line.uppercase()
        val compact = normalizeForSku(line)
        val lettersAndNumbers = upper
            .replace(Regex("""[^A-Z0-9]"""), "")
            .replace("O", "0")
            .replace("I", "1")
            .replace("L", "1")

        val readableRangeText = upper
            .replace("_", "")
            .replace(" ", "")
            .replace("â", "-")

        Regex("""PH[O0]?114-1B-50""", RegexOption.IGNORE_CASE)
            .find(upper)
            ?.let { return "PH0114-1B-50" }

        Regex("""PH0114-1B-50""", RegexOption.IGNORE_CASE)
            .find(upper)
            ?.let { return "PH0114-1B-50" }

        Regex("""PH2844-1V-100""", RegexOption.IGNORE_CASE)
            .find(upper)
            ?.let { return "PH2844-1V-100" }

        Regex("""PH4662-1V-100""", RegexOption.IGNORE_CASE)
            .find(upper)
            ?.let { return "PH4662-1V-100" }

        if (
            compact.contains("PH2844") ||
            compact.contains("PH28") ||
            upper.contains("2.8-4.4") ||
            readableRangeText.contains("2.8-4.4") ||
            lettersAndNumbers.contains("PH28441V100")
        ) {
            return "PH2844-1V-100"
        }

        if (
            compact.contains("PH4662") ||
            compact.contains("PH46") ||
            upper.contains("4.6-6.2") ||
            readableRangeText.contains("4.6-6.2") ||
            lettersAndNumbers.contains("PH46621V100")
        ) {
            return "PH4662-1V-100"
        }

        if (
            upper.contains("PH PAPER", ignoreCase = true) ||
            upper.contains("PH PAP", ignoreCase = true) ||
            upper.contains("1 TO 14", ignoreCase = true) ||
            upper.contains("1-14", ignoreCase = true) ||
            lettersAndNumbers.contains("PHPAPER") ||
            lettersAndNumbers.contains("1T014") ||
            lettersAndNumbers.contains("PACK0F50STRIPS") ||
            compact.contains("PH0114") ||
            compact.contains("PH01141B50")
        ) {
            return "PH0114-1B-50"
        }

        return null
    }

    private fun normalizeMoreFlavorSku(rawSku: String, fullLine: String): String? {
        val upperLine = fullLine.uppercase()
        val cleaned = rawSku.uppercase()
            .replace("O", "0")
            .replace(Regex("""[^A-Z0-9-]"""), "")
        val readableLine = upperLine
            .replace("_", "")
            .replace(" ", "")
            .replace("â", "-")

        return when {
            cleaned == "PH01141B50" ||
                    cleaned == "PH01141B5O" ||
                    cleaned == "PH01141BSO" -> "PH0114-1B-50"

            rawSku.uppercase().startsWith("PHO114", ignoreCase = true) -> "PH0114-1B-50"
            rawSku.uppercase().startsWith("PH0114", ignoreCase = true) -> "PH0114-1B-50"
            rawSku.uppercase().startsWith("PH2844", ignoreCase = true) -> "PH2844-1V-100"
            rawSku.uppercase().startsWith("PH4662", ignoreCase = true) -> "PH4662-1V-100"

            upperLine.contains("2.8-4.4") || readableLine.contains("2.8-4.4") -> "PH2844-1V-100"
            upperLine.contains("4.6-6.2") || readableLine.contains("4.6-6.2") -> "PH4662-1V-100"
            upperLine.contains("PH PAPER") || upperLine.contains("1 TO 14") -> "PH0114-1B-50"

            looksLikeSku(rawSku) -> rawSku.uppercase()
            else -> null
        }
    }

    private fun descriptionFromLine(line: String, sku: String): String {
        val beforeQuantity = line.substringBefore("$")
            .replace(sku, "", ignoreCase = true)
            .replace("PHO114-1B-50", "", ignoreCase = true)
            .replace("PH0114-1B-50", "", ignoreCase = true)
            .replace("PH2844-1V-100", "", ignoreCase = true)
            .replace("PH4662-1V-100", "", ignoreCase = true)
            .replace(Regex("""\s+\d+(?:\.\d+)?\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return beforeQuantity.ifBlank { sku }
    }

    private fun addMoreFlavorItem(
        items: MutableList<ParsedPdfItem>,
        seen: MutableSet<String>,
        sku: String,
        fallbackDescription: String,
        quantity: Double,
        unitPrice: Double
    ) {
        val normalizedSku = normalizeMoreFlavorSku(sku, fallbackDescription) ?: sku.uppercase()
        val key = "$normalizedSku|$quantity|$unitPrice"
        if (!seen.add(key)) return

        items.add(
            item(
                sku = normalizedSku,
                description = ItemMapper.getItemDescription(normalizedSku).ifBlank {
                    fallbackDescription.replace(Regex("""\s+"""), " ").trim().ifBlank { normalizedSku }
                },
                quantity = quantity,
                unitPrice = unitPrice
            )
        )
    }

    private fun looksLikeSku(value: String): Boolean {
        return Regex("""^[A-Z0-9]+(?:-[A-Z0-9]+)+$""", RegexOption.IGNORE_CASE).matches(value.trim())
    }

    private fun looksLikeItemStart(line: String): Boolean {
        val trimmed = line.trim()
        return Regex(
            """^[A-Z0-9]+(?:-[A-Z0-9]+)+\s+.+\s+[\d.]+\s+\$[\d.]+\s+\$[\d,]+\.\d{2}$""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(trimmed) || resolveSkuFromLine(trimmed) != null && trimmed.contains("$")
    }

    private fun looksLikeFooter(line: String): Boolean {
        val text = normalize(line)
        return text.startsWith("TOTAL:") ||
                text.startsWith("TOTA:") ||
                text.startsWith("TOTALS") ||
                text.startsWith("NOTE:") ||
                text.startsWith("*PERISHABLE*") ||
                text.startsWith("SHELFLIFE") ||
                text.startsWith("OR1YEAR") ||
                text.startsWith("AVAILABLE,") ||
                text.startsWith("VENDORPLEASECONFIRM") ||
                text.startsWith("PLEASEHELPUSVERIFY") ||
                text.startsWith("THANKYOUSOMUCH") ||
                text.startsWith("BESTREGARDS")
    }

    private fun looksLikePerishableNote(line: String): Boolean {
        val text = normalize(line)
        return text.contains("*PERISHABLE*") ||
                text.contains("SHELFLIFE") ||
                text.contains("OR1YEAR") ||
                text.contains("OLDERSTOCK") ||
                text.contains("PURCHASING@MOREFLAVOR.COM") ||
                text.contains("CURRENTSTOCKAGE") ||
                text.startsWith("NOTE:")
    }

    private fun pretty(value: String?): String? {
        if (value.isNullOrBlank()) return value
        return value
            .replace("MoreFlavor,Inc.", "MoreFlavor, Inc.")
            .replace("16335JohnGlenn", "16335 John Glenn")
            .replace("NewCentury", "New Century")
            .trim()
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private fun normalizeForSku(text: String): String {
        return text.uppercase()
            .replace("O", "0")
            .replace("I", "1")
            .replace("L", "1")
            .replace(Regex("""[^A-Z0-9.\-]"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private data class CityStateZip(
        val city: String,
        val state: String,
        val zip: String
    )


}
