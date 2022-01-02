package com.republicate.skorm

open class Database(name: String): AttributeHolder(name) {
    val schemas = mutableMapOf<String, Schema>()
    private lateinit var processor: Processor

}
