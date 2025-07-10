package com.republicate.skorm

typealias ValueFilter = (Any?) -> Any?

val identityFilter: ValueFilter = { it }

class ValuesFiltering private constructor(): Transformers<String, Any?>(stockFilters) {
    companion object {
        private val lowercase: ValueFilter = { it?.toString()?.toLowerCase() }
        private val uppercase: ValueFilter = { it?.toString()?.toUpperCase() }

        private val stockFilters = mapOf(
            "lowercase" to lowercase,
            "uppercase" to uppercase,
        )
        private val instance = ValuesFiltering()
        operator fun get(name: String) = instance[name]
        operator fun set(name: String, op: (Any?)->Any?) {
            instance[name] = op
        }
    }
}
