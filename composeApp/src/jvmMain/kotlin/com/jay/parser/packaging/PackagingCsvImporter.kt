package com.jay.parser.packaging

import org.apache.commons.csv.CSVFormat
import java.io.File

class PackagingCsvImporter {

    data class ParsedPackagingData(
        val products: Map<String, ProductPackaging>,
        val warnings: List<String>
    )

    fun parse(file: File): ParsedPackagingData {
        require(file.extension.equals("csv", ignoreCase = true)) {
            "Packaging database must be a .csv file."
        }

        val warnings = mutableListOf<String>()
        val products = linkedMapOf<String, ProductPackaging>()

        file.bufferedReader().use { reader ->
            CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build()
                .parse(reader)
                .use { parser ->
                    val headers = parser.headerMap.keys.associateBy { normalizeHeader(it) }
                    val skuHeader = headers["SKU"]
                        ?: error("Packaging CSV must contain a SKU column.")
                    val lengthHeader = headers["LENGTH (IN)"]
                        ?: error("Packaging CSV must contain Length (in).")
                    val widthHeader = headers["WIDTH (IN)"]
                        ?: error("Packaging CSV must contain Width (in).")
                    val heightHeader = headers["HEIGHT (IN)"]
                        ?: error("Packaging CSV must contain Height (in).")
                    val weightHeader = headers["WEIGHT (LBS)"]
                        ?: error("Packaging CSV must contain Weight (lbs).")

                    parser.forEach { record ->
                        val rawSku = record.get(skuHeader).trim()
                        if (rawSku.isBlank()) return@forEach

                        val sku = normalizeSku(rawSku)
                        var length = parseNumber(record.get(lengthHeader), sku, "length", record.recordNumber, warnings)
                        val width = parseNumber(record.get(widthHeader), sku, "width", record.recordNumber, warnings)
                        val height = parseNumber(record.get(heightHeader), sku, "height", record.recordNumber, warnings)
                        val weight = parseNumber(record.get(weightHeader), sku, "weight", record.recordNumber, warnings)

                        val inferredLength = length == null && width != null && height != null && isVialSku(sku)
                        if (inferredLength) length = width

                        if (products.containsKey(sku)) {
                            warnings += "Duplicate packaging SKU replaced: $sku"
                        }

                        products[sku] = ProductPackaging(
                            lengthInches = length,
                            widthInches = width,
                            heightInches = height,
                            weightPounds = weight,
                            inferredLength = inferredLength
                        )
                    }
                }
        }

        require(products.isNotEmpty()) { "Packaging CSV did not contain any products." }
        return ParsedPackagingData(products, warnings)
    }

    private fun parseNumber(
        raw: String,
        sku: String,
        field: String,
        recordNumber: Long,
        warnings: MutableList<String>
    ): Double? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val parsed = value.replace(",", "").toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) {
            warnings += "Ignored invalid $field for $sku on CSV row ${recordNumber + 1}: $value"
            return null
        }
        return parsed
    }

    private fun normalizeHeader(value: String): String = value
        .removePrefix("\uFEFF")
        .trim()
        .uppercase()
        .replace(Regex("""\s+"""), " ")

    private fun normalizeSku(value: String): String {
        val normalized = value.trim().uppercase().replace(" ", "")
        return if (normalized.matches(Regex("""(?:240|285|290)-(?:24|500)-810"""))) {
            normalized.removeSuffix("810") + "8X10"
        } else {
            normalized
        }
    }

    private fun isVialSku(sku: String): Boolean =
        Regex("""(?:^|-)\d+V(?:B)?(?:-|$)""").containsMatchIn(sku)
}
