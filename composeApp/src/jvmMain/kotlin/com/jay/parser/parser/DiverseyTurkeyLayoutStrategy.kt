package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields

class DiverseyTurkeyLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "Diversey Turkey"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))

        return text.contains("DIVERSEYKIMYASANAYIVETICARETAS") &&
                (
                        text.contains("4535188038") ||
                                text.contains("70014627") ||
                                text.contains("10612V100") ||
                                text.contains("SPC14512V100")
                        )
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))

        var score = 0
        if (text.contains("DIVERSEYKIMYASANAYIVETICARETAS")) score += 100
        if (text.contains("4535188038")) score += 90
        if (text.contains("10612V100")) score += 70
        if (text.contains("SPC14512V100")) score += 70
        if (text.contains("QACTESTKIT")) score += 30
        if (text.contains("CHLORINETEST")) score += 30
        if (text.contains("PURCHASEORDER")) score += 20

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
            addressLine2 = parseAddressLine2(clean),
            city = parseCity(clean),
            state = null,
            zip = null,
            terms = mappedCustomer?.terms,
            items = parseItems(clean, mappedCustomer?.priceLevel)
        )
    }

    private fun parseCustomerName(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("DIVERSEYKIMYASANAYIVETICARETAS")
        }?.let { "Diversey Kimya Sanayi ve Ticaret A.S." }
    }

    private fun parseOrderNumber(lines: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i].replace(Regex("""\s+"""), " ").trim()

            val inline = Regex(
                """Purchase\s*Order.*?(\d{8,12})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(line)
            if (inline != null) {
                return inline.groupValues[1].trim()
            }

            if (line.matches(Regex("""^\d{8,12}$"""))) {
                return line
            }

            val embedded = Regex("""\b(\d{8,12})\b""").find(line)
            if (embedded != null && embedded.groupValues[1] == "4535188038") {
                return embedded.groupValues[1]
            }
        }

        val joined = lines.joinToString(" ")
        return Regex("""\b(\d{8,12})\b""").findAll(joined)
            .map { it.groupValues[1] }
            .firstOrNull { it == "4535188038" }
    }

    private fun parseAddressLine1(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("BARBAROSMAHIHLAMURBULNO3")
        }?.let { "Barbaros Mah. Ihlamur Bul. No: 3" }
    }

    private fun parseAddressLine2(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ICKAPINO67ATASEHIRISTANBULTURKEY")
        }?.let { "İç Kapı No: 67 Atasehir/ Istanbul - Turkey" }
    }

    private fun parseCity(lines: List<String>): String? {
        return lines.firstOrNull {
            compact(it).contains("ATASEHIRISTANBULTURKEY")
        }?.let { "Atasehir / Istanbul" }
    }

    private fun parseItems(lines: List<String>, priceLevel: String?) = buildList {
        val seen = mutableSetOf<String>()

        for (line in lines) {
            val normalized = line.replace(Regex("""\s+"""), " ").trim()

            val match = Regex(
                """^\d+\s+\d+\s+([A-Z0-9-]+)\s+(.+?)\s+(\d+(?:,\d{3})?)\s+packets$""",
                RegexOption.IGNORE_CASE
            ).find(normalized) ?: continue

            val sku = normalizeSku(match.groupValues[1].trim().uppercase())
            val description = match.groupValues[2].trim()
            val quantity = match.groupValues[3].replace(",", "").toDoubleOrNull()

            if (quantity == null) continue

            val finalDescription = ItemMapper.getItemDescription(sku).ifBlank { description }

            val mappedPrice = ItemMapper.getItemPrice(sku, priceLevel)
            val unitPrice = if (mappedPrice == 0.0) null else mappedPrice

            val key = "$sku|$quantity"
            if (!seen.add(key)) continue

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

    private fun compact(value: String): String {
        return value.uppercase()
            .replace("İ", "I")
            .replace(Regex("""[^A-Z0-9]"""), "")
    }
}