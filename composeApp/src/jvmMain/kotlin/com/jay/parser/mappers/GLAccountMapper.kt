package com.jay.parser.mappers

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

object GLAccountMapper {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val glMapping: Map<String, String> by lazy {
        val stream = object {}.javaClass.getResourceAsStream("/data/glAccounts.json")
            ?: error("Could not find /data/glAccounts.json")

        val text = stream.bufferedReader().use { it.readText() }

        json.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            text
        )
    }

    fun getGLAccount(sku: String?): String {
        if (sku.isNullOrBlank()) return ""

        val normalizedSku = sku.trim().uppercase()

        glMapping[normalizedSku]?.let { return it }

        val prefix = normalizedSku.substringBefore("-")
        glMapping[prefix]?.let { return it }

        return ""
    }
}