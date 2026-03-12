package com.jay.parser.pdf

import java.io.File

fun main() {
    val pdfFile = File("testpdf/WEBB CHEMICAL PO 024652.PDF")

    if (!pdfFile.exists()) {
        error("PDF not found: ${pdfFile.absolutePath}")
    }

    val extractor = PdfTextExtractor()
    val parser = PdfFieldParser()

    val textLines = extractor.extractLines(pdfFile)
    val parsed = parser.parse(textLines)

    println("==================================================")
    println("PDF DEBUG")
    println("==================================================")
    println("File: ${pdfFile.name}")
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

    if (parsed.items.isNotEmpty()) {
        println("---- PARSED ITEMS ----")
        parsed.items.forEachIndexed { index, item ->
            println("Item ${index + 1}")
            println("  sku         = ${item.sku}")
            println("  description = ${item.description}")
            println("  quantity    = ${item.quantity}")
            println("  unitPrice   = ${item.unitPrice}")
            println()
        }
    } else {
        println("---- PARSED ITEMS ----")
        println("(none)")
        println()
    }

    println("==================================================")
}