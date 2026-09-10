package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem
import java.time.DateTimeException
import java.time.LocalDate

class CovenantAviationLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "COVENANT AVIATION SECURITY"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return (
                text.contains("COVENANTAVIATION") ||
                        text.contains("COVENANTAVIATIONSECURITY")
                ) && (
                text.contains("MALLORY") ||
                        text.contains("PO#") ||
                        text.contains("TSAPER100")
                )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("COVENANTAVIATIONSECURITYLLC")) score += 120
        if (text.contains("COVENANTAVIATION")) score += 80
        if (text.contains("MALLORYSAFETY")) score += 80
        if (text.contains("TSAPER100")) score += 80
        if (text.contains("CAS549650WJR")) score += 80
        if (text.contains("FREMONTCA94539")) score += 60
        if (text.contains("NET30")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val shipTo = parseShipTo(clean)
        val customerName = parseCustomerName(clean)
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = shipTo.addressLine2,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean) ?: mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        val firstMatches = lines.take(6)

        for (line in firstMatches) {
            val compactLine = compact(line)
            if (compactLine.contains("COVENANTAVIATIONSECURITYLLC")) {
                return "Covenant Aviation Security, LLC"
            }
            if (compactLine.contains("COVENANTAVIATIONSECURITY")) {
                return "Covenant Aviation Security, LLC"
            }
        }

        return lines.firstOrNull {
            compact(it).contains("COVENANTAVIATION")
        }?.trim()
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        val date = lines
            .firstNotNullOfOrNull(::parseDateLine)
            ?: return null

        return "COV%02d%02d%02d".format(
            date.monthValue,
            date.dayOfMonth,
            date.year % 100
        )
    }

    private fun parseDateLine(line: String): LocalDate? {
        if (!line.contains("DATE", ignoreCase = true)) return null

        val normalized = line.replace(Regex("""\s+"""), " ").trim()
        val exact = Regex(
            """DATE\s*[:;]?\s*[^0-9]*(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(20\d{2})""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (exact != null) {
            return validDate(
                year = exact.groupValues[3].toInt(),
                month = exact.groupValues[1].toInt(),
                day = exact.groupValues[2].toInt()
            )
        }

        val yearMatch = Regex("""20\d{2}""").find(normalized) ?: return null
        val dateStart = normalized.indexOf("DATE", ignoreCase = true)
        if (dateStart < 0 || yearMatch.range.last < dateStart) return null

        val ocrSignal = normalized
            .substring(dateStart + 4, yearMatch.range.last + 1)
            .uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .filter { it.isDigit() || it == '/' }
            .replace('/', '7')
        val year = yearMatch.value.toInt()

        return (1..12).asSequence()
            .flatMap { month ->
                (1..31).asSequence().mapNotNull { day -> validDate(year, month, day) }
            }
            .singleOrNull { candidate ->
                "${candidate.monthValue}7${candidate.dayOfMonth}7${candidate.year}" == ocrSignal
            }
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun parseTerms(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """TERMS\s*[:\-]?\s*(.+)$""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (match != null) {
                return match.groupValues[1].trim()
            }

            if (compact(normalized).contains("NET30")) {
                return "Net 30"
            }
        }

        return null
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        var shipToCustomer: String? = null
        var addressLine1: String? = null
        var addressLine2: String? = null
        var city: String? = null
        var state: String? = null
        var zip: String? = null

        val shipIndex = lines.indexOfFirst {
            val c = compact(it)
            c.contains("SHIPTO") || c.contains("MALLORYSAFETY")
        }

        val searchWindow = if (shipIndex >= 0) {
            lines.drop(shipIndex).take(8)
        } else {
            lines.take(20)
        }

        for (line in searchWindow) {
            val trimmed = line.replace(Regex("""\s+"""), " ").trim()
            val compactLine = compact(trimmed)

            if (shipToCustomer == null && compactLine.contains("MALLORYSAFETY")) {
                shipToCustomer = "Mallory Safety & Supply LLC"
                continue
            }

            if (addressLine1 == null && compactLine.contains("44380OSGOODROAD")) {
                addressLine1 = "44380 Osgood Road"
                continue
            }

            if (addressLine2 == null && compactLine.contains("ATTN")) {
                addressLine2 = trimmed
                continue
            }

            val csz = Regex(
                """^(FREMONT),\s*(CA)\s*(94539(?:-\d{4})?)$""",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (csz != null) {
                city = "Fremont"
                state = "CA"
                zip = csz.groupValues[3].trim()
                continue
            }

            if (city == null && compactLine.contains("FREMONTCA94539")) {
                city = "Fremont"
                state = "CA"
                zip = "94539"
            }
        }

        return ShipToBlock(
            shipToCustomer = shipToCustomer,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            state = state,
            zip = zip
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()

        for (i in lines.indices) {
            if (!compact(lines[i]).contains("TSAPER100")) continue

            val sku = "TSAPER100"
            val priceValues = nearbyIndices(i, lines.lastIndex)
                .map { lines[it].replace(Regex("""\s+"""), " ").trim() }
                .filterNot { compact(it).contains("TOTAL") }
                .map { monetaryValues(it) }
                .firstOrNull { it.size >= 2 }
                ?: continue
            val money = priceValues
            val unitPrice = money[money.lastIndex - 1]
            val extPrice = money.last()

            val quantity = if (unitPrice != 0.0) {
                extPrice / unitPrice
            } else {
                continue
            }

            val normalizedQuantity = if (quantity % 1.0 == 0.0) {
                quantity.toInt().toDouble()
            } else {
                quantity
            }

            val description = ItemMapper.getItemDescription(sku).ifBlank { sku }

            val key = "$sku|$normalizedQuantity|$unitPrice"
            if (!seen.add(key)) continue

            items.add(
                item(
                    sku = sku,
                    description = description,
                    quantity = normalizedQuantity,
                    unitPrice = unitPrice
                )
            )
        }

        return items
    }

    private fun nearbyIndices(center: Int, lastIndex: Int): Sequence<Int> = sequence {
        yield(center)
        for (distance in 1..lastIndex) {
            val before = center - distance
            val after = center + distance
            if (before >= 0) yield(before)
            if (after <= lastIndex) yield(after)
        }
    }

    private fun monetaryValues(line: String): List<Double> = Regex("""\d[\d,]*\.\d{2}""")
        .findAll(line)
        .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
        .toList()

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9#]"""), "")
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )
}
