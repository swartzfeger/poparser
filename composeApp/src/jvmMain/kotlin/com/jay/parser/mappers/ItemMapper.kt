package com.jay.parser.mappers

import com.jay.parser.models.ItemCatalog
import kotlinx.serialization.json.Json

object ItemMapper {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val catalog: ItemCatalog by lazy {
        val stream = object {}.javaClass.getResourceAsStream("/data/items.json")
            ?: error("Could not find /data/items.json")

        val text = stream.bufferedReader().use { it.readText() }
        json.decodeFromString<ItemCatalog>(text)
    }

    private val sortedPriceKeys: List<String> by lazy {
        catalog.prices.keys.sortedByDescending { it.length }
    }

    private val sortedDescriptionKeys: List<String> by lazy {
        catalog.descriptions.keys.sortedByDescending { it.length }
    }

    fun getItemDescription(sku: String?): String {
        if (sku.isNullOrBlank()) return ""

        val normalizedSku = sku.trim().uppercase()

        catalog.descriptions[normalizedSku]?.let { return it }

        for (key in sortedDescriptionKeys) {
            if (normalizedSku.startsWith(key)) {
                return catalog.descriptions[key].orEmpty()
            }
        }

        return ""
    }

    fun getItemPrice(sku: String?, priceLevel: String?): Double {
        if (sku.isNullOrBlank() || priceLevel.isNullOrBlank()) return 0.0

        val normalizedSku = sku.trim().uppercase()
        val normalizedLevel = priceLevel.trim()

        catalog.prices[normalizedSku]?.get(normalizedLevel)?.let { return it }

        for (key in sortedPriceKeys) {
            if (normalizedSku.startsWith(key)) {
                return catalog.prices[key]?.get(normalizedLevel) ?: 0.0
            }
        }

        return 0.0
    }
}