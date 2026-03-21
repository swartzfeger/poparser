package com.jay.parser.pdf

import java.io.File

fun main() {
    val testFiles = listOf(
        //"testpdf/Precision 2025.08.29(406390).pdf",
        //"testpdf/Precision 2025.12.16(406460).pdf",
        "testpdf/Precision 2025.12.30(406468).pdf",
        //"testpdf/Precision 2025.12.30(406469)Maintex.pdf",
        //"testpdf/Precision 2025.12.30406467)FastenalPA.pdf"
    )

    val extractor = PdfTextExtractor()
    val parser = PdfFieldParser()

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

        println("---- PARSED ITEMS ----")
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

        println("==================================================")
        println()
    }

    println("==================================================")
    println("BATCH DEBUG COMPLETE")
    println("==================================================")
}