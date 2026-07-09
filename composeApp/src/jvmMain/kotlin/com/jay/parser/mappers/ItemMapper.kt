package com.jay.parser.mappers

import com.jay.parser.masterdata.MasterDataStore

object ItemMapper {

    fun getItemDescription(sku: String?): String {
        if (sku.isNullOrBlank()) return ""

        val normalizedSku = sku.trim().uppercase()
        val catalog = MasterDataStore.current().itemCatalog

        catalog.descriptions[normalizedSku]?.let { return it }

        for (key in catalog.descriptions.keys.sortedByDescending { it.length }) {
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
        val catalog = MasterDataStore.current().itemCatalog

        catalog.prices[normalizedSku]?.get(normalizedLevel)?.let { return it }

        for (key in catalog.prices.keys.sortedByDescending { it.length }) {
            if (normalizedSku.startsWith(key)) {
                return catalog.prices[key]?.get(normalizedLevel) ?: 0.0
            }
        }

        return 0.0
    }

    // --- NEW FUNCTION ADDED BELOW ---

    /**
     * Exposes all known database SKUs for fuzzy matching in the OrderEnricher.
     */
    fun getAllSkus(): List<String> {
        return MasterDataStore.current().itemCatalog.descriptions.keys.toList()
    }
}
