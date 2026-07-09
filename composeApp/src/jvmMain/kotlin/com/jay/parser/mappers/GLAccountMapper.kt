package com.jay.parser.mappers

import com.jay.parser.masterdata.MasterDataStore

object GLAccountMapper {

    fun getGLAccount(sku: String?): String {
        if (sku.isNullOrBlank()) return ""

        val normalizedSku = sku.trim().uppercase()
        val glMapping = MasterDataStore.current().glAccounts

        glMapping[normalizedSku]?.let { return it }

        val prefix = normalizedSku.substringBefore("-")
        glMapping[prefix]?.let { return it }

        return ""
    }
}
