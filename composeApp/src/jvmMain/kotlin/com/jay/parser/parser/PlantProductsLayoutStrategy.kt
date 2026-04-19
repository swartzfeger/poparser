package com.jay.parser.parser

import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class PlantProductsLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {

    override val name: String = "PLANT PRODUCTS"

    override fun matches(lines: List<String>): Boolean {
        val text = normalize(lines.joinToString("\n"))
        return text.contains("PLANTPRODUCTSINC.") ||
                text.contains("PLANTPRODUCTSLEAMINGTON") ||
                text.contains("GAGE.GABRIELE@PLANTPRODUCTS.COM") ||
                (text.contains("PO#:") && text.contains("SUPPLIERDELIVERYADDRESS"))
    }

    override fun score(lines: List<String>): Int {
        val text = normalize(lines.joinToString("\n"))
        var score = 0

        if (text.contains("PLANTPRODUCTSINC.")) score += 120
        if (text.contains("PLANTPRODUCTSLEAMINGTON")) score += 100
        if (text.contains("GAGE.GABRIELE@PLANTPRODUCTS.COM")) score += 80
        if (text.contains("SUPPLIERDELIVERYADDRESS")) score += 70
        if (text.contains("LEAMINGTON,ON,N8H3W1")) score += 70
        if (text.contains("NET30DAYS")) score += 50

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { it.replace(Regex("""\s+"""), " ").trim() }
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = "PLANT PRODUCTS INC.",
            orderNumber = parseOrderNumber(clean),
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = parseTerms(clean),
            items = parseItems(clean)
        )
    }

    private fun prettifyPlantProductsText(value: String?): String? {
        if (value.isNullOrBlank()) return value

        return value
            .replace("PlantProductsLeamington", "Plant Products Leamington")
            .replace("50HazeltonStreet", "50 Hazelton Street")
            .trim()
    }
    private fun parseOrderNumber(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return Regex("""PO#:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(joined)
            ?.groupValues
            ?.get(1)
    }

    private fun parseTerms(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("NET30DAYS", ignoreCase = true) -> "Net 30 Days"
            joined.contains("NET 30 DAYS", ignoreCase = true) -> "Net 30 Days"
            else -> null
        }
    }

    private fun parseShipTo(lines: List<String>): ShipToBlock {
        val idx = lines.indexOfFirst { normalize(it) == "SUPPLIERDELIVERYADDRESS" }
        if (idx >= 0) {
            val companyLine = lines.getOrNull(idx + 1).orEmpty()
            val addressLine = lines.getOrNull(idx + 2).orEmpty()
            val cityLine = lines.getOrNull(idx + 3).orEmpty()

            val company = prettifyPlantProductsText(
                splitSupplierDeliveryLine(companyLine).second ?: "Plant Products Leamington"
            )

            val address1 = prettifyPlantProductsText(
                splitSupplierDeliveryLine(addressLine).second ?: "50 Hazelton Street"
            )

            val cityStateZip = splitSupplierDeliveryLine(cityLine).second ?: "Leamington, ON, N8H 3W1"
            val parsed = parseCanadianCityStateZip(cityStateZip)

            return ShipToBlock(
                shipToCustomer = company,
                addressLine1 = address1,
                city = parsed?.city,
                state = parsed?.state,
                zip = parsed?.zip
            )
        }

        return ShipToBlock(
            shipToCustomer = "Plant Products Leamington",
            addressLine1 = "50 Hazelton Street",
            city = "Leamington",
            state = "ON",
            zip = "N8H 3W1"
        )
    }

    private fun splitSupplierDeliveryLine(line: String): Pair<String?, String?> {
        val trimmed = line.trim()

        val vendorPrefixes = listOf(
            "Precision Laboratories (WIRE)(7320)",
            "PrecisionLaboratories(WIRE)(7320)",
            "415 South Airpark Road",
            "415 SouthAirparkRoad",
            "Cottonwood, Arizona, 86326",
            "Cottonwood,Arizona,86326"
        )

        for (prefix in vendorPrefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                val right = trimmed.removePrefix(prefix).trim()
                return prefix to right.ifBlank { null }
            }
        }

        val parts = Regex("""\s{2,}""").split(trimmed).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> null to trimmed
        }
    }

    private fun parseCanadianCityStateZip(text: String?): CityStateZip? {
        if (text.isNullOrBlank()) return null

        val cleaned = text
            .replace("Leamington,ON,N8H3W1", "Leamington, ON, N8H 3W1")
            .replace("Leamington,ON,N8H 3W1", "Leamington, ON, N8H 3W1")
            .trim()

        val match = Regex("""^(.+?),\s*([A-Z]{2}),\s*([A-Z]\d[A-Z]\s?\d[A-Z]\d)$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?: return null

        val postal = match.groupValues[3]
            .trim()
            .replace(" ", "")
            .let { "${it.substring(0, 3)} ${it.substring(3)}" }

        return CityStateZip(
            city = match.groupValues[1].trim(),
            state = match.groupValues[2].trim().uppercase(),
            zip = postal
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()
        val seen = mutableSetOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            val match = Regex(
                """^(\d+)\s+\((\d+)\)(.+?)\s+([\d.]+)\s+EA\s+([\d.]+)\s+([\d,]+\.\d{2})$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            if (match != null) {
                val descriptionStart = match.groupValues[3].trim()
                val quantity = match.groupValues[4].toDoubleOrNull()
                val unitPrice = match.groupValues[5].toDoubleOrNull()

                if (quantity == null || unitPrice == null) {
                    i++
                    continue
                }

                val descriptionParts = mutableListOf(descriptionStart)

                val nextLine = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (nextLine.isNotBlank() &&
                    !nextLine.startsWith("Supp#:", ignoreCase = true) &&
                    !looksLikeNewItem(nextLine) &&
                    !looksLikeFooter(nextLine)
                ) {
                    descriptionParts.add(nextLine)
                    i += 1
                }

                val suppLine = lines.getOrNull(i + 1)?.trim().orEmpty()
                val sku = Regex("""Supp#:\s*([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
                    .find(suppLine)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.uppercase()

                if (!sku.isNullOrBlank()) {
                    val mappedDescription = ItemMapper.getItemDescription(sku).ifBlank { "" }
                    val description = if (mappedDescription.isNotBlank()) {
                        mappedDescription
                    } else {
                        descriptionParts.joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    }

                    val key = "$sku|$quantity|$unitPrice"
                    if (seen.add(key)) {
                        items.add(
                            item(
                                sku = sku,
                                description = description,
                                quantity = quantity,
                                unitPrice = unitPrice
                            )
                        )
                    }

                    i += 1
                }
            }

            i++
        }

        return items
    }

    private fun looksLikeNewItem(line: String): Boolean {
        return Regex("""^\d+\s+\(\d+\)""").containsMatchIn(line)
    }

    private fun looksLikeFooter(line: String): Boolean {
        val text = normalize(line)
        return text.startsWith("TOTAL:") ||
                text.startsWith("BUYER:") ||
                text.startsWith("PLEASEHAVELOTNUMBERS") ||
                text.startsWith("****PURCHASEORDERNUMBER") ||
                text.startsWith("FORCOMPLETEPURCHASEORDER") ||
                text.startsWith("REMITINVOICETO:")
    }

    private fun normalize(text: String): String {
        return text.uppercase()
            .replace(Regex("""\s+"""), "")
            .trim()
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
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