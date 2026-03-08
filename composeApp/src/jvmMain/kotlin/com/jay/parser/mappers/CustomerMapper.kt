package com.jay.parser.mappers

import com.jay.parser.models.MasterCustomer
import kotlinx.serialization.json.Json

object CustomerMapper {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val customerData: List<MasterCustomer> by lazy {
        val stream = object {}.javaClass.getResourceAsStream("/data/customers.json")
            ?: error("Could not find /data/customers.json")

        val text = stream.bufferedReader().use { it.readText() }
        json.decodeFromString<List<MasterCustomer>>(text)
    }

    fun lookupCustomer(name: String?): MasterCustomer? {
        if (name.isNullOrBlank()) return null

        val normalized = name.trim().uppercase()

        val matchById = customerData.find { it.id.uppercase() == normalized }
        if (matchById != null) return matchById

        val matchByName = customerData.find { it.name.uppercase() == normalized }
        if (matchByName != null) return matchByName

        val partialMatch = customerData.find {
            val customerId = it.id.uppercase()
            val customerName = it.name.uppercase()

            normalized.contains(customerId) ||
                    customerId.contains(normalized) ||
                    normalized.contains(customerName) ||
                    customerName.contains(normalized)
        }

        return partialMatch
    }
}