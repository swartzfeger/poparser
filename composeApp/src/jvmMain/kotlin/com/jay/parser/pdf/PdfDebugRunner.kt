package com.jay.parser.pdf

import com.jay.parser.parser.OrderEnricher
import java.io.File

fun main() {
    val testFiles = listOf(
        //"testpdf/Dove PO-00226-D - Prec Lab.pdf",
        //"testpdf/Dove PO-00348-D - Prec Lab.pdf",
        //"testpdf/Dove PO-00452-D - Prec Lab.pdf",
        "testpdf/FRESENIUS MEDICAL.pdf",
        //"testpdf/Dove POD-0087 - PrecLab.pdf"
    )

    val extractor = PdfTextExtractor()
    val parser = PdfFieldParser()
    val enricher = OrderEnricher()

    println("==================================================")
    println("BATCH PDF DEBUG")
    println("==================================================")
    println("Files queued: ${testFiles.size}")
    println()

    testFiles.forEachIndexed { fileIndex, path ->
        val pdfFile = File(path)

        println("##################################################")
        println("PDF ${fileIndex + 1} of ${testFiles.size}")
        println("##################################################")
        println("Requested path: $path")

        if (!pdfFile.exists()) {
            println("ERROR: PDF not found: ${pdfFile.absolutePath}")
            println()
            return@forEachIndexed
        }

        val textLines = extractor.extractLines(pdfFile)
        val parsed = parser.parse(textLines)
        val enriched = enricher.enrich(pdfFile.name, parsed)

        println("==================================================")
        println("PDF DEBUG")
        println("==================================================")
        println("File: ${pdfFile.name}")
        println("Absolute path: ${pdfFile.absolutePath}")
        println("Line count: ${textLines.size}")
        println()

        println("---- RAW TEXT LINES ----")
        textLines.forEachIndexed { index, line ->
            println("${index + 1}: $line")
        }
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