package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class CompanhiaNitroLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Companhia Nitro"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("COMPANHIANITROQUIMICABRASILEIRA") &&
                text.contains("BTGPACTUALCOMMODITIESSERTRADINGSA") &&
                text.contains("SERTRADINGREFERENCE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("COMPANHIANITROQUIMICABRASILEIRA")) score += 160
        if (text.contains("BTGPACTUALCOMMODITIESSERTRADINGSA")) score += 140
        if (text.contains("SERTRADINGREFERENCE")) score += 120
        if (text.contains("CNPJ61150348000150")) score += 100
        if (text.contains("CNPJ04626426000297")) score += 80
        if (text.contains("INTERNATIONALISPMNUMBER15")) score += 60
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "BTG PACTUAL COMMODITIES SERTRADING S.A.",
            addressLine1 = "AV. CORONEL MARCOS KONDER, 950",
            addressLine2 = "SALA 8, EDIF. VALENTI",
            city = "Itajaí",
            state = "SC",
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        for (i in lines.indices) {
            val row = ITEM_ROW_PATTERN.matchEntire(lines[i]) ?: continue
            val partialSku = row.groupValues[1].uppercase()
            val sku = if (partialSku.endsWith("-")) {
                val continuation = lines.getOrNull(i + 1)
                    ?.let { SKU_CONTINUATION_PATTERN.find(it)?.groupValues?.get(1) }
                    ?: continue
                "$partialSku${continuation.uppercase()}"
            } else {
                partialSku
            }

            val quantity = parseBrazilianNumber(row.groupValues[2]) ?: continue
            val unitPrice = parseBrazilianNumber(row.groupValues[3]) ?: continue

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice
                )
            )
        }
    }

    private fun parseBrazilianNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private companion object {
        const val CUSTOMER_ID = "COMPANHIA NITRO"

        val ORDER_NUMBER_PATTERN = Regex(
            """SERTRADING\s*REFERENCE\s*:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )

        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+)*-?)\s+.+?\s+\d{8}\s+([\d.]+,\d+)\s+[A-Z]+\s+([\d.]+,\d+)\s+[\d.]+,\d+\s+USD(?:\s+.*)?$""",
            RegexOption.IGNORE_CASE
        )

        val SKU_CONTINUATION_PATTERN = Regex("""^(\d+)\b""")
    }
}
