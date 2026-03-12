package com.jay.parser.parser

import com.jay.parser.pdf.ParsedPdfFields

interface LayoutStrategy {
    val name: String

    /**
     * Returns true if this strategy looks like a good fit for the extracted lines.
     */
    fun matches(lines: List<String>): Boolean

    /**
     * Returns a score so we can choose the best matching strategy when multiple match.
     */
    fun score(lines: List<String>): Int

    /**
     * Parse the PDF lines into structured fields.
     */
    fun parse(lines: List<String>): ParsedPdfFields
}