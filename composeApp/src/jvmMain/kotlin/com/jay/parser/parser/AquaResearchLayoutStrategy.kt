package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class AquaResearchLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "AQUA RESEARCH"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("AQUARESEARCHINC") &&
                (
                        text.contains("ARPO092530") ||
                                text.contains("MRPEASYCOM") ||
                                text.contains("5601MIDWAYPARKPLNE") ||
                                text.contains("AQRCHL51B25")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("AQUARESEARCHINC")) score += 100
        if (text.contains("ARPO092530")) score += 90
        if (text.contains("5601MIDWAYPARKPLNE")) score += 70
        if (text.contains("ALBUQUERQUENM87109")) score += 70
        if (text.contains("PRECISIONLABS")) score += 20
        if (text.contains("AQRCHL51B25")) score += 80
        if (text.contains("MRPEASYCOM")) score += 30

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines)
        val customerName = parseCustomerName(clean)
        val mappedCustomer = CustomerMapper.lookupCustomer(customerName)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = customerName,
            addressLine1 = parseAddressLine1(clean),
            addressLine2 = null,
            city = parseCity(clean),
            state = parseState(clean),
            zip = parseZip(clean),
            terms = mappedCustomer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("AQUARESEARCHINC")
        }?.let { "Aqua Research Inc." }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """Purchase\s*order\s*([A-Z0-9-]+)""",
                RegexOption.IGNORE_CASE
            ).find(normalized)

            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        return lines.firstOrNull {
            compact(it).contains("ARPO092530")
        }?.let { "AR-PO-0925-30" }
    }

    private fun parseAddressLine1(lines: List<String>): String? {
        val buyerIndex = lines.indexOfFirst { compact(it).contains("BUYERAQUARESEARCHINC") }
        if (buyerIndex >= 0) {
            for (i in (buyerIndex + 1)..minOf(buyerIndex + 4, lines.lastIndex)) {
                val line = lines[i].trim()
                if (compact(line).contains("5601MIDWAYPARKPLNE")) {
                    return "5601 Midway Park pl NE"
                }
            }
        }

        return lines.firstOrNull {
            compact(it).contains("5601MIDWAYPARKPLNE")
        }?.let { "5601 Midway Park pl NE" }
    }

    private fun parseCity(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ALBUQUERQUENM87109")
        }?.let { "Albuquerque" }
    }

    private fun parseState(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ALBUQUERQUENM87109")
        }?.let { "NM" }
    }

    private fun parseZip(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ALBUQUERQUENM87109")
        }?.let { "87109" }
    }

    private fun parseItems(lines: List<String>) = buildList {
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^\d+\s+([A-Z0-9-]+)\s+([A-Z0-9-]+)\s+(.+?)\s+([A-Z0-9-]+)\s+([\d,]+)\s+\$\s*([\d,]+\.\d{2})\s+\$\s*([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(normalized) ?: continue

            val vendorSku = normalizeSku(match.groupValues[4].trim().uppercase())
            val description = match.groupValues[3].trim()
            val quantity = match.groupValues[5].replace(",", "").toDoubleOrNull()
            val unitPrice = match.groupValues[6].replace(",", "").toDoubleOrNull()

            if (quantity == null || unitPrice == null) continue

            val finalDescription = ItemMapper.getItemDescription(vendorSku).ifBlank {
                description
            }

            val key = "$vendorSku|$quantity|$unitPrice"
            if (!seen.add(key)) continue

            add(
                item(
                    sku = vendorSku,
                    description = finalDescription,
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun compact(value: String): String {
        return value.uppercase().replace(Regex("""[^A-Z0-9]"""), "")
    }
}