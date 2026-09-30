package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class RilabLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = "RILAB"

    override fun matches(lines: List<String>): Boolean {
        val text = compactDocument(lines)
        return looksLikeOdooPurchaseOrder(text) || looksLikeLegacyPurchaseOrder(text)
    }

    override fun score(lines: List<String>): Int {
        val text = compactDocument(lines)
        var score = 0
        if (text.contains("CARLOSRIVASPADILLA") && text.contains("LIMITADA")) score += 200
        if (text.contains("RUT773143803")) score += 180
        if (text.contains("RILABCL")) score += 160
        if (text.contains("HUECHURABACLRM8581151")) score += 140
        if (text.contains("ORDENDECOMPRA")) score += 120
        if (text.contains("RILABSPA") && text.contains("520031855")) score += 200
        if (text.contains("PURCHASEORDER")) score += 140
        if (text.contains("QTYUNITCODEDESCRIPTION")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val legacyPurchaseOrder = looksLikeLegacyPurchaseOrder(compactDocument(clean))
        val legacyShipTo = if (legacyPurchaseOrder) parseLegacyShipTo(clean) else null

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = parseOrderNumber(clean, legacyPurchaseOrder),
            shipToCustomer = if (legacyPurchaseOrder) legacyShipTo?.customer else SHIP_TO_CUSTOMER,
            addressLine1 = if (legacyPurchaseOrder) legacyShipTo?.addressLine1 else ADDRESS_LINE_1,
            addressLine2 = if (legacyPurchaseOrder) legacyShipTo?.addressLine2 else ADDRESS_LINE_2,
            city = if (legacyPurchaseOrder) legacyShipTo?.city else CITY,
            state = if (legacyPurchaseOrder) legacyShipTo?.state else STATE,
            zip = if (legacyPurchaseOrder) legacyShipTo?.zip else ZIP,
            terms = customer?.terms,
            items = if (legacyPurchaseOrder) parseLegacyItems(clean) else parseOdooItems(clean)
        )
    }

    private fun parseOrderNumber(lines: List<String>, legacyPurchaseOrder: Boolean): String? {
        lines.firstNotNullOfOrNull { line ->
            ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)?.uppercase()
        }?.let { return it }

        if (!legacyPurchaseOrder) return null
        val purchaseOrderIndex = lines.indexOfFirst { line ->
            compact(line).contains("PURCHASEORDER")
        }
        if (purchaseOrderIndex < 0) return null

        return lines
            .drop(purchaseOrderIndex)
            .take(3)
            .firstNotNullOfOrNull { line ->
                LEGACY_ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            }
    }

    private fun parseOdooItems(lines: List<String>): List<ParsedPdfItem> {
        val seen = mutableSetOf<String>()
        return lines.mapNotNull { line ->
            val bracketMatches = BRACKETED_SKU_PATTERN.findAll(line).toList()
            if (bracketMatches.isEmpty()) return@mapNotNull null

            val preferredSkuMatch = bracketMatches.firstOrNull { match ->
                match.groupValues[1].startsWith("H40", ignoreCase = true)
            } ?: bracketMatches.first()
            val sku = normalizeOdooSku(preferredSkuMatch.groupValues[1])
                ?: return@mapNotNull null
            val dataStart = bracketMatches.last().range.last + 1
            val numericValues = SPANISH_DECIMAL_PATTERN
                .findAll(line.substring(dataStart))
                .map { match -> match.value }
                .toList()
            if (numericValues.size < 4) return@mapNotNull null
            val quantity = numericValues.getOrNull(numericValues.size - 4)?.let(::parseSpanishNumber)
                ?: return@mapNotNull null
            val unitPrice = numericValues.getOrNull(numericValues.size - 3)?.let(::parseSpanishNumber)
                ?: return@mapNotNull null
            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) return@mapNotNull null

            parsedItem(sku, quantity, unitPrice)
        }
    }

    private fun parseLegacyItems(lines: List<String>): List<ParsedPdfItem> {
        val headerIndex = lines.indexOfFirst { line ->
            compact(line).contains("QTYUNITCODEDESCRIPTION")
        }
        if (headerIndex < 0) return emptyList()

        val itemLines = lines.drop(headerIndex + 1).takeWhile { line ->
            !compact(line).startsWith("SUBTOTAL")
        }
        val seen = mutableSetOf<String>()

        return itemLines.mapIndexedNotNull { index, line ->
            val skuMatch = LEGACY_SKU_PATTERN.find(line) ?: return@mapIndexedNotNull null
            val sku = skuMatch.value.uppercase()
            val beforeSku = line.substring(0, skuMatch.range.first)
            val afterSku = line.substring(skuMatch.range.last + 1)

            val quantity = TRAILING_NUMBER_PATTERN.find(beforeSku)?.groupValues?.get(1)
                ?.let(::parseSpanishNumber)
                ?: itemLines.getOrNull(index + 1)
                    ?.let { LEADING_NUMBER_PATTERN.find(it)?.groupValues?.get(1) }
                    ?.let(::parseSpanishNumber)
                ?: return@mapIndexedNotNull null

            val unitPrice = MONEY_PATTERN.find(afterSku)?.groupValues?.get(1)
                ?.let(::parseSpanishNumber)
                ?: itemLines.getOrNull(index - 1)
                    ?.let { MONEY_PATTERN.find(it)?.groupValues?.get(1) }
                    ?.let(::parseSpanishNumber)
                ?: return@mapIndexedNotNull null

            val key = "$sku|$quantity|$unitPrice"
            if (!seen.add(key)) return@mapIndexedNotNull null

            parsedItem(sku, quantity, unitPrice)
        }
    }

    private fun parsedItem(sku: String, quantity: Double, unitPrice: Double): ParsedPdfItem = item(
        sku = sku,
        description = ItemMapper.getItemDescription(sku).ifBlank { sku },
        quantity = quantity,
        unitPrice = unitPrice
    )

    private fun normalizeOdooSku(raw: String): String? {
        val sku = raw.uppercase().removePrefix("H40").removePrefix("-")
        return sku.takeIf { LEGACY_SKU_PATTERN.matches(it) }
    }

    private fun parseLegacyShipTo(lines: List<String>): LegacyShipTo? {
        val shipTo = lines.firstNotNullOfOrNull { line ->
            LEGACY_SHIP_TO_PATTERN.find(line)?.groupValues?.get(1)
        } ?: return null
        val parts = shipTo.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        val addressLine1 = parts.getOrNull(0)?.replace(ADDRESS_NUMBER_PATTERN, "$1 $2")
        return LegacyShipTo(
            customer = LEGACY_SHIP_TO_CUSTOMER,
            addressLine1 = addressLine1,
            addressLine2 = parts.getOrNull(3),
            city = parts.getOrNull(1),
            state = parts.getOrNull(2),
            zip = null
        )
    }

    private fun parseSpanishNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace("Ñ", "N")
        .replace(Regex("""[^A-Z0-9]"""), "")

    private fun compactDocument(lines: List<String>): String = lines.joinToString("") { line ->
        collapseDuplicatedCharacters(compact(line))
    }

    private fun collapseDuplicatedCharacters(value: String): String {
        if (value.length < 8) return value
        val pairs = value.length / 2
        val duplicatePairs = (0 until pairs).count { index ->
            value[index * 2] == value[index * 2 + 1]
        }
        if (duplicatePairs < pairs * 0.75) return value

        return buildString(pairs + value.length % 2) {
            for (index in 0 until pairs) append(value[index * 2])
            if (value.length % 2 != 0) append(value.last())
        }
    }

    private fun looksLikeOdooPurchaseOrder(text: String): Boolean =
        text.contains("ORDENDECOMPRA") &&
                text.contains("773143803") &&
                (text.contains("CARLOSRIVASPADILLA") || text.contains("HUECHURABACLRM8581151")) &&
                (text.contains("RILABCL") || text.contains("H40PAA") || text.contains("H40QAC"))

    private fun looksLikeLegacyPurchaseOrder(text: String): Boolean =
        text.contains("RILABSPA") &&
                text.contains("520031855") &&
                text.contains("PURCHASEORDER") &&
                text.contains("QTYUNITCODEDESCRIPTION")

    private data class LegacyShipTo(
        val customer: String,
        val addressLine1: String?,
        val addressLine2: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private companion object {
        const val CUSTOMER_ID = "RILAB"
        const val SHIP_TO_CUSTOMER = "Carlos Rivas Padilla y Compañía Limitada"
        const val ADDRESS_LINE_1 = "Av del Valle Sur 570"
        const val ADDRESS_LINE_2 = "Of 102, Ciudad Empresarial, Chile"
        const val CITY = "Huechuraba"
        const val STATE = "RM"
        const val ZIP = "8581151"
        const val LEGACY_SHIP_TO_CUSTOMER = "Rilab SPA"

        val ORDER_NUMBER_PATTERN = Regex(
            """ORDEN\s+DE\s+COMPRA\s*#\s*(P\d+)""",
            RegexOption.IGNORE_CASE
        )
        val LEGACY_ORDER_NUMBER_PATTERN = Regex("""\b(\d{1,8})\s*$""")
        val BRACKETED_SKU_PATTERN = Regex(
            """\[([A-Z0-9]+(?:-[A-Z0-9]+){2,})]""",
            RegexOption.IGNORE_CASE
        )
        val SPANISH_DECIMAL_PATTERN = Regex("""\d[\d.]*,\d{2}""")
        val LEGACY_SKU_PATTERN = Regex("""[A-Z0-9]+(?:-[A-Z0-9]+){2,}""", RegexOption.IGNORE_CASE)
        val TRAILING_NUMBER_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*$""")
        val LEADING_NUMBER_PATTERN = Regex("""^\s*(\d+(?:[.,]\d+)?)\s+""")
        val MONEY_PATTERN = Regex("""\$\s*([\d.,]+)""")
        val LEGACY_SHIP_TO_PATTERN = Regex("""SHIP\s*TO\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)
        val ADDRESS_NUMBER_PATTERN = Regex("""([A-Za-z])(?=\d)|(?<=\d)([A-Za-z])""")
    }
}
