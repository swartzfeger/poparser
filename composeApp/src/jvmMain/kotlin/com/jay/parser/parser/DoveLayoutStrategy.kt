package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields
import com.jay.parser.pdf.ParsedPdfItem

class DoveLayoutStrategy : BaseLayoutStrategy() {

    override val name: String = "Dove Material Suppliers"

    override fun matches(lines: List<String>): Boolean {
        val text = lines.joinToString("\n").uppercase()
        return text.contains("DOVE MATERIAL SUPPLIERS") &&
                text.contains("PRECISION LABORATORIES")
    }

    override fun score(lines: List<String>): Int {
        val text = lines.joinToString("\n").uppercase()

        var score = 0
        if (text.contains("DOVE MATERIAL SUPPLIERS")) score += 100
        if (text.contains("PRECISION LABORATORIES")) score += 80
        if (Regex("""\b(PO-\d+-D|POD-\d+)\b""").containsMatchIn(text)) score += 40
        if (text.contains("CUPPED OAK")) score += 60
        if (text.contains("MATTHEWS NORTH CAROLINA")) score += 60
        if (text.contains("FRYOILSAVER")) score += 40

        return score
    }

    override fun parse(lines: List<String>): ParsedPdfFields {
        val textLines = nonBlankLines(lines)

        val customerName = "DOVE MATERIAL SUPPLIERS USA, LLC"
        val orderNumber = findFirstMatch(
            textLines,
            Regex("""\b(PO-\d+-D|POD-\d+)\b""", RegexOption.IGNORE_CASE)
        )

        val shipTo = findDoveShipTo(textLines)
        val usesTotalEachColumn = hasTotalEachColumn(textLines)
        val items = findDoveItems(textLines, usesTotalEachColumn)

        return ParsedPdfFields(
            customerName = customerName,
            orderNumber = orderNumber,
            shipToCustomer = shipTo.shipToCustomer,
            addressLine1 = shipTo.addressLine1,
            addressLine2 = null,
            city = shipTo.city,
            state = shipTo.state,
            zip = shipTo.zip,
            terms = null,
            items = items
        )
    }

    private data class ShipToBlock(
        val shipToCustomer: String?,
        val addressLine1: String?,
        val city: String?,
        val state: String?,
        val zip: String?
    )

    private fun findDoveShipTo(lines: List<String>): ShipToBlock {
        val defaultCustomer = "Dove d.b.a. SpecTank Carolinas / The FryOilSaver Company"

        val addressLine = lines
            .firstOrNull { it.contains("Cupped Oak", ignoreCase = true) }
            ?.let(::normalizeDoveAddress)

        val cityLine = lines
            .firstOrNull {
                it.contains("Matthews", ignoreCase = true) &&
                        it.contains("North Carolina", ignoreCase = true)
            }

        val zip = cityLine
            ?.let { Regex("""\b(\d{5})\b""").find(it)?.groupValues?.get(1) }

        return ShipToBlock(
            shipToCustomer = defaultCustomer,
            addressLine1 = addressLine,
            city = if (cityLine != null) "MATTHEWS" else null,
            state = if (cityLine != null) "NC" else null,
            zip = zip
        )
    }

    private fun normalizeDoveAddress(line: String): String {
        return line
            .replace(
                Regex("""^\s*\(?\d{3}\)?[\s.-]*\d{3}[\s.-]*\d{4}\s+"""),
                ""
            )
            .replace("Drv", "Dr")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun hasTotalEachColumn(lines: List<String>): Boolean {
        val joined = lines.joinToString(" ").uppercase()
            .replace(Regex("""\s+"""), " ")

        val hasTotalEach = joined.contains("TOTAL EACH")
        val hasCaseRequired = joined.contains("CASE REQUIRED") || joined.contains("CASE QTY REQUIRED")
        val hasEachPrice = joined.contains("EACH PRICE")
        val hasPricePerCase = joined.contains("PRICE PER CASE")

        return hasTotalEach && hasCaseRequired && hasEachPrice && hasPricePerCase
    }

    private fun findDoveItems(
        lines: List<String>,
        usesTotalEachColumn: Boolean
    ): List<ParsedPdfItem> {
        val items = mutableListOf<ParsedPdfItem>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (isDoveMainItemLine(line)) {
                val quantity = extractDoveQuantity(line, usesTotalEachColumn)
                val sku = buildDoveSku(lines, i)

                if (!sku.isNullOrBlank() && quantity != null) {
                    items += ParsedPdfItem(
                        sku = normalizeDoveSku(sku),
                        description = null,
                        quantity = quantity.toDouble(),
                        unitPrice = null
                    )
                }
            }

            i++
        }

        return items
    }

    private fun isDoveMainItemLine(line: String): Boolean {
        val u = line.uppercase()

        if (u.startsWith("UPC ")) return false
        if (u.startsWith("LABEL CODE")) return false
        if (u.startsWith("USA -")) return false
        if (u.startsWith("CANADA -")) return false
        if (u.startsWith("CANADA-")) return false
        if (u.startsWith("CASE-")) return false
        if (u.startsWith("PACKS UPC")) return false
        if (u.startsWith("OTHER INSTRUCTIONS")) return false
        if (u.startsWith("PRODUCT DESCRIPTION")) return false
        if (u.startsWith("SUPPLIER ")) return false
        if (u.startsWith("SKU QTY")) return false
        if (u.startsWith("QTY TOTAL")) return false
        if (u.startsWith("SUB TOTAL")) return false
        if (u.startsWith("SUBTOTAL")) return false
        if (u.startsWith("TOTAL (USD)")) return false
        if (u.startsWith("TOTALDISCOUNTONITEMS")) return false

        return Regex("""\$\d{1,3}(,\d{3})*\.\d{2}\s*$""").containsMatchIn(line) &&
                u.contains("0.00 %")
    }

    private fun extractDoveQuantity(
        line: String,
        usesTotalEachColumn: Boolean
    ): Int? {
        val money = """\$[\d,]+(?:\.\d{1,4})?"""

        return if (usesTotalEachColumn) {
            // Examples from PO-00523-D:
            // 500 4000 $0.9639 $481.95 8 0.00 % $3,855.60
            // 100 3000 $4.0000 $400 30 0.00 % $12,000.00
            // 100 500 $4.7000 $470 5 0.00 % $2,350.00
            // 100 4000 $2.1300 $213 40 0.00 % $8,520.00
            val match = Regex(
                """\b(\d+)\s+(\d+)\s+$money\s+$money\s+(\d+)\s+0\.00\s*%\s+\$[\d,]+\.\d{2}\s*$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            match?.groupValues?.get(2)?.toIntOrNull()
        } else {
            // Example:
            // 500 8 $463.50 $0.927 4000 0.00 % $3,708.00
            val match = Regex(
                """\b\d+\s+\d+\s+$money\s+$money\s+(\d+)\s+0\.00\s*%\s+\$[\d,]+\.\d{2}\s*$""",
                RegexOption.IGNORE_CASE
            ).find(line)

            match?.groupValues?.get(1)?.toIntOrNull()
        }
    }

    private fun buildDoveSku(lines: List<String>, index: Int): String? {
        val window = listOfNotNull(
            lines.getOrNull(index),
            lines.getOrNull(index + 1),
            lines.getOrNull(index + 2)
        ).joinToString(" ")

        val compact = window
            .uppercase()
            .replace('\uFFFE', '-')
            .replace("–", "-")
            .replace("—", "-")
            .replace("'", "")
            .replace(Regex("""\s+"""), " ")

        return when {
            compact.contains("145-500V-") && compact.contains("100") ->
                "145-500V-100"

            compact.contains("145-4VB-100") ||
                    (compact.contains("145-4VB-") && compact.contains("100")) ->
                "145-4VB-100"

            compact.contains("145-QR5-2VB-100") ||
                    (compact.contains("145-QR5-") && compact.contains("2VB-100")) ||
                    (compact.contains("COMBO") && compact.contains("145-QR5-") && compact.contains("2VB-")) ->
                "145-QR5-2VB-100"

            compact.contains("106-QR5-4VB-100") ||
                    (compact.contains("106-QR5-") && compact.contains("4VB-100")) ||
                    (compact.contains("QR5 4-PACK") && compact.contains("106-QR5-")) ->
                "106-QR5-4VB-100"

            compact.contains("106-QR5-500V-100") ||
                    (compact.contains("106-QR5-") && compact.contains("500V-100")) ||
                    (compact.contains("106-QR5-") && compact.contains("500V-") && compact.contains("100")) ->
                "106-QR5-500V-100"

            else -> null
        }
    }

    private fun normalizeDoveSku(raw: String): String {
        return raw
            .uppercase()
            .replace('\uFFFE', '-')
            .replace("–", "-")
            .replace("—", "-")
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""-CANADA\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*COMBO\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""-COMBO\b""", RegexOption.IGNORE_CASE), "")
            .trim()
            .removeSuffix("-")
    }
}