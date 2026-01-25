package com.republicate.skorm

import com.republicate.kson.Json

typealias ValueFilter = (Any?) -> Any?

val identityFilter: ValueFilter = { it }

class ValuesFiltering private constructor(): Transformers<String, Any?>(stockFilters) {
    companion object {
        private val lowercase: ValueFilter = { it?.toString()?.lowercase() }
        private val uppercase: ValueFilter = { it?.toString()?.uppercase() }
        private val parseJson: ValueFilter = { value ->
            when (value) {
                null -> null
                is Json -> value
                is String -> Json.parse(value)
                is ByteArray -> {
                    // Some databases (H2) return JSON as a JSON-encoded string in byte array
                    // e.g., byte[] decodes to "{\"name\": \"Alice\"}" instead of {"name": "Alice"}
                    val str = value.decodeToString()
                    val parsed = Json.parseValue(str)  // parseValue handles any JSON value, not just containers
                    // If result is a String, it was double-encoded - parse the inner content
                    if (parsed is String) Json.parse(parsed) else parsed
                }
                else -> Json.parse(value.toString())
            }
        }

        private val stockFilters = mapOf(
            "lowercase" to lowercase,
            "uppercase" to uppercase,
            "parseJson" to parseJson,
        )
        private val instance = ValuesFiltering()
        operator fun get(name: String) = instance[name]
        operator fun set(name: String, op: (Any?)->Any?) {
            instance[name] = op
        }
    }
}
