package com.republicate.skorm

open class Database(name: String): AttributeHolder(name) {
    private val _schemas = mutableMapOf<String, Schema>()
    private lateinit var processor: Processor

    val schemas: Map<String, Schema> by _schemas
}
