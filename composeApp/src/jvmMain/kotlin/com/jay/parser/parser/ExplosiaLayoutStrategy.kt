package com.jay.parser.parser

import com.jay.parser.mappers.CustomerMapper
import com.jay.parser.mappers.ItemMapper
import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class ExplosiaLayoutStrategy : BaseLayoutStrategy(), LayoutStrategy {
    override val name: String = CUSTOMER_ID

    override fun matches(lines: List<String>): Boolean {
        val text = compact(lines.joinToString("\n"))
        return text.contains("EXPLOSIA") &&
                text.contains("DRAFTPURCHASEAGREEMENT") &&
                text.contains("MILITARYMETHYLVIOLETSTRIPS") &&
                text.contains("INSTRUCTIONSFORTRANSPORTATION") &&
                text.contains("CENTRALRECEIPTOFDELIVERIES") &&
                text.contains("2202002070")
    }

    override fun score(lines: List<String>): Int {
        val text = compact(lines.joinToString("\n"))
        var score = 0
        if (text.contains("EXPLOSIA")) score += 240
        if (text.contains("DRAFTPURCHASEAGREEMENT")) score += 220
        if (text.contains("MARIEMADROVAEXPLOSIACZ")) score += 180
        if (text.contains("MILITARYMETHYLVIOLETSTRIPS")) score += 180
        if (text.contains("INSTRUCTIONSFORTRANSPORTATION")) score += 160
        if (text.contains("CENTRALRECEIPTOFDELIVERIES")) score += 140
        if (text.contains("2202002070")) score += 120
        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val clean = nonBlankLines(lines).map { line ->
            line.replace(Regex("""\s+"""), " ").trim()
        }
        val customer = CustomerMapper.lookupCustomer(CUSTOMER_ID)
        val shipTo = parseShipTo(clean)

        return ParsedPdfFields(
            customerName = CUSTOMER_ID,
            orderNumber = clean.firstNotNullOfOrNull { line ->
                ORDER_NUMBER_PATTERN.find(line)?.groupValues?.get(1)
            },
            shipToCustomer = "EXPLOSIA a.s. - Central receipt of deliveries",
            addressLine1 = shipTo?.addressLine1,
            addressLine2 = shipTo?.addressLine2,
            city = shipTo?.city,
            state = "Czech Republic",
            zip = shipTo?.zip,
            terms = customer?.terms,
            items = parseItems(clean)
        )
    }

    private fun parseShipTo(lines: List<String>): ShipTo? {
        val mailingLine = lines.firstNotNullOfOrNull { line ->
            SHIP_TO_MAIL_PATTERN.find(line)
        } ?: return null
        val centralReceipt = lines.firstNotNullOfOrNull { line ->
            CENTRAL_RECEIPT_PATTERN.find(line)?.groupValues?.get(1)?.trim()
        }

        return ShipTo(
            addressLine1 = mailingLine.groupValues[1].trim(),
            addressLine2 = centralReceipt?.replace(Regex(""",\s*"""), ", "),
            city = mailingLine.groupValues[3].trim(),
            zip = mailingLine.groupValues[2].replace(Regex("""\s+"""), " ")
        )
    }

    private fun parseItems(lines: List<String>): List<ParsedPdfItem> = buildList {
        lines.forEachIndexed { index, line ->
            val sku = SKU_PATTERN.matchEntire(line)?.groupValues?.get(1)?.uppercase()
                ?: return@forEachIndexed
            val quantityRow = ((index - 1) downTo maxOf(0, index - 4))
                .firstNotNullOfOrNull { rowIndex -> QUANTITY_ROW_PATTERN.matchEntire(lines[rowIndex]) }
                ?: return@forEachIndexed
            val quantity = parseEuropeanNumber(quantityRow.groupValues[1]) ?: return@forEachIndexed
            val unitPrice = parseEuropeanNumber(quantityRow.groupValues[2]) ?: return@forEachIndexed

            add(
                item(
                    sku = sku,
                    description = ItemMapper.getItemDescription(sku).ifBlank { sku },
                    quantity = quantity,
                    unitPrice = unitPrice,
                    uom = "PIECES"
                )
            )
        }
    }

    private fun parseEuropeanNumber(value: String): Double? = value
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    private fun compact(value: String): String = value
        .uppercase()
        .replace(Regex("""[^A-Z0-9]"""), "")

    private data class ShipTo(
        val addressLine1: String,
        val addressLine2: String?,
        val city: String,
        val zip: String
    )

    private companion object {
        const val CUSTOMER_ID = "EXPLOSIA as"

        val ORDER_NUMBER_PATTERN = Regex(
            """\bNO\.?:\s*(\d{8,12})\b""",
            RegexOption.IGNORE_CASE
        )
        val SHIP_TO_MAIL_PATTERN = Regex(
            """INDIVIDUAL\s+PACKAGE\s+AND\s+MAIL\s+DELIVERIES:\s*EXPLOSIA\s+A\.S\.,\s*(.+?),\s*(\d{3}\s*\d{2})\s+(.+?),\s*CZECH\s+REPUBLIC""",
            RegexOption.IGNORE_CASE
        )
        val CENTRAL_RECEIPT_PATTERN = Regex(
            """CENTRAL\s+RECEIPT\s+OF\s+DELIVERIES:\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val SKU_PATTERN = Regex(
            """^(220-200-2070)$""",
            RegexOption.IGNORE_CASE
        )
        val QUANTITY_ROW_PATTERN = Regex(
            """^([\d.]+,\d{3})\s+PIECES\s+/200\s+([\d.]+,\d{2})$""",
            RegexOption.IGNORE_CASE
        )
    }
}
