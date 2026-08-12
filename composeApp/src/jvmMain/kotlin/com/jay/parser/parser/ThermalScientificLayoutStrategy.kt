package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ThermalScientificLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "Thermal Scientific / General Laboratory Supply"

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("VENDORIDV47120") &&
                text.contains("ACCOUNTNOTHERMALSC") &&
                text.contains("APTHERMALSCIENTIFICCOM") &&
                text.contains("POBOX2273") &&
                text.contains("ITEMUOMQTYUNITPRICEEXTENDEDPRICE")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("VENDORIDV47120")) score += 220
        if (text.contains("ACCOUNTNOTHERMALSC")) score += 190
        if (text.contains("APTHERMALSCIENTIFICCOM")) score += 170
        if (text.contains("POBOX2273")) score += 150
        if (text.contains("ITEMUOMQTYUNITPRICEEXTENDEDPRICE")) score += 130
        if (text.contains("THERMALSCIENTIFIC") || text.contains("GENERALLABSUPPLY")) score += 110
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val text = compact(clean.joinToString("\n"))
        val isGeneralLaboratory = text.contains("GENERALLABSUPPLYATHERMALSCIENTIFICCOMPANY")
        val customerId = if (isGeneralLaboratory) GENERAL_LAB_CUSTOMER_ID else THERMAL_CUSTOMER_ID
        val customer = CustomerMapper.lookupCustomer(customerId)
        val shipTo = resolveShipTo(text, isGeneralLaboratory)

        return ParsedPdfFields(
            customerName = customerId,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
            },
            shipToCustomer = shipTo.customer,
            addressLine1 = shipTo.addressLine1,
            city = shipTo.city,
            state = "TX",
            zip = shipTo.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun resolveShipTo(text: String, isGeneralLaboratory: Boolean): ShipTo = when {
        isGeneralLaboratory -> ShipTo(
            customer = "General Lab Supply - a Thermal Scientific Company",
            addressLine1 = "2835 Preston Ave",
            city = "Pasadena",
            zip = "77503-3821"
        )

        text.contains("12633EFM917STE101") -> ShipTo(
            customer = "Thermal Scientific, Inc.",
            addressLine1 = "12633 E FM 917 Ste 101",
            city = "Alvarado",
            zip = "76009-6158"
        )

        else -> ShipTo(
            customer = "Thermal Scientific, Inc.",
            addressLine1 = "2702 Westover Dr",
            city = "Odessa",
            zip = "79764-1439"
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = lines.mapNotNull { line ->
        val match = ITEM_ROW_PATTERN.matchEntire(line) ?: return@mapNotNull null
        val sku = match.groupValues[1].uppercase()
        val quantity = parseNumber(match.groupValues[4]) ?: return@mapNotNull null
        val unitPrice = parseNumber(match.groupValues[5]) ?: return@mapNotNull null

        item(
            sku = sku,
            description = ItemMapper.getItemDescription(sku).ifBlank { sku },
            quantity = quantity,
            unitPrice = unitPrice
        )
    }

    private fun parseNumber(value: String): Double? = value
        .replace(",", "")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val customer: String,
        val addressLine1: String,
        val city: String,
        val zip: String
    )

    private companion object {
        const val THERMAL_CUSTOMER_ID = "THERMAL SCIENTIFIC"
        const val GENERAL_LAB_CUSTOMER_ID = "GENERAL LABORATORY"

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDER\s*NO\s*\.\s*:\s*([A-Z]{2}\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ITEM_ROW_PATTERN = Regex(
            """^([A-Z0-9]+(?:-[A-Z0-9]+){2,})\s+(.+?)\s+(PACK|DOZEN|CASE)\s+([\d,]+(?:\.\d+)?)\s+([\d,]+(?:\.\d+)?)\s+[\d,]+(?:\.\d+)?$""",
            RegexOption.IGNORE_CASE
        )
    }
}
