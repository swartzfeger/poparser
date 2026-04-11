package com.jay.parser.pdf

import com.jay.parser.parser.OrderEnricher
import com.jay.parser.parser.OrderFileParser
import java.io.File

fun main() {
    val testFiles = listOf(
        //"testpdf/Dove PO-00226-D - Prec Lab.pdf",
        //"testpdf/Dove PO-00348-D - Prec Lab.pdf",
        //"testpdf/Dove PO-00452-D - Prec Lab.pdf",
        "testpdf/COVENANT AVIATION - TSA DISTRIBUTOR Scan2026-01-19_112238 precision.pdf",
        //"testpdf/Dove POD-0087 - PrecLab.pdf"
    )

    val fileParser = OrderFileParser()
    val enricher = OrderEnricher()

    println("==================================================")
    println("BATCH ORDER DEBUG")
    println("==================================================")
    println("Files queued: ${testFiles.size}")
    println()

    testFiles.forEachIndexed { fileIndex, path ->
        val inputFile = File(path)

        println("##################################################")
        println("FILE ${fileIndex + 1} of ${testFiles.size}")
        println("##################################################")
        println("Requested path: $path")

        if (!inputFile.exists()) {
            println("ERROR: File not found: ${inputFile.absolutePath}")
            println()
            return@forEachIndexed
        }

        val ocrExtractor = OcrPdfTextExtractor()
        val rawOcrLines = ocrExtractor.extractLines(inputFile)

        println("---- RAW OCR LINES ----")
        rawOcrLines.forEachIndexed { index, line ->
            println("${index + 1}: ${line.text}")
        }
        println()

        val parsed = fileParser.parse(inputFile)
        val enriched = enricher.enrich(inputFile.name, parsed)

        println("==================================================")
        println("ORDER DEBUG")
        println("==================================================")
        println("File: ${inputFile.name}")
        println("Absolute path: ${inputFile.absolutePath}")
        println()

        println("---- PARSED FIELDS ----")
        println("customerName     = ${parsed.customerName}")
        println("orderNumber      = ${parsed.orderNumber}")
        println("shipToCustomer   = ${parsed.shipToCustomer}")
        println("addressLine1     = ${parsed.addressLine1}")
        println("addressLine2     = ${parsed.addressLine2}")
        println("city             = ${parsed.city}")
        println("state            = ${parsed.state}")
        println("zip              = ${parsed.zip}")
        println("terms            = ${parsed.terms}")
        println("items.count      = ${parsed.items.size}")
        println()

        println("---- PARSED ITEMS (RAW) ----")
        if (parsed.items.isNotEmpty()) {
            parsed.items.forEachIndexed { index, item ->
                println("Item ${index + 1}")
                println("  sku         = ${item.sku}")
                println("  description = ${item.description}")
                println("  quantity    = ${item.quantity}")
                println("  unitPrice   = ${item.unitPrice}")
                println()
            }
        } else {
            println("(none)")
            println()
        }

        println("---- ENRICHED ORDER ----")
        println("customer.id         = ${enriched.customer?.id}")
        println("customer.name       = ${enriched.customer?.name}")
        println("customer.priceLevel = ${enriched.customer?.priceLevel}")
        println("termsResolved       = ${enriched.termsResolved}")
        println("lines.count         = ${enriched.lines.size}")
        println()

        println("---- ENRICHED LINES ----")
        if (enriched.lines.isNotEmpty()) {
            enriched.lines.forEachIndexed { index, line ->
                println("Line ${index + 1}")
                println("  sku               = ${line.sku}")
                println("  description       = ${line.description}")
                println("  quantityRaw       = ${line.quantityRaw}")
                println("  quantityForExport = ${line.quantityForExport}")
                println("  unitPriceRef      = ${line.unitPriceReference}")
                println("  unitPriceResolved = ${line.unitPriceResolved}")
                println("  glAccount         = ${line.glAccount}")
                println()
            }
        } else {
            println("(none)")
            println()
        }

        println("==================================================")
        println()
    }

    println("==================================================")
    println("BATCH DEBUG COMPLETE")
    println("==================================================")
}