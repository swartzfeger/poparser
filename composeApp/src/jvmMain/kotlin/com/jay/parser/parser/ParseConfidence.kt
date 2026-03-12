package com.jay.parser.parser

data class ParseConfidence(
    val orderNumberConfidence: Double = 0.0,
    val customerConfidence: Double = 0.0,
    val shipToConfidence: Double = 0.0,
    val itemConfidence: Double = 0.0
)