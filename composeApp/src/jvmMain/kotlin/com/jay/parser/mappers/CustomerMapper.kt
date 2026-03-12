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

        val normalized = normalize(name)

        val matchById = customerData.find { normalize(it.id) == normalized }
        if (matchById != null) return matchById

        val matchByName = customerData.find { normalize(it.name) == normalized }
        if (matchByName != null) return matchByName

        val partialMatch = customerData.find {
            val customerId = normalize(it.id)
            val customerName = normalize(it.name)

            normalized.contains(customerId) ||
                    customerId.contains(normalized) ||
                    normalized.contains(customerName) ||
                    customerName.contains(normalized)
        }

        return partialMatch
    }

    private fun normalize(value: String): String {
        return value
            .uppercase()
            .replace(Regex("""\([^)]*\)"""), " ")   // remove things like (200)
            .replace("&", " AND ")
            .replace(Regex("""[^A-Z0-9]+"""), " ")  // collapse punctuation/hyphens/slashes
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}