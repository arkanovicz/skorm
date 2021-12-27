package com.republicate.skorm

open class Database(name: String, internal val processor: Processor) : AttributeHolder(name) {
    val voidShema = Schema("", this)
    private val schemas = mutableMapOf<String, Schema>()

    private lateinit var connector: Connector
    fun connect(connector: Connector) { this.connector = connector }

    fun getSchema(schemaName: String) = schemas[schemaName] ?: throw SQLException("unknown schema: $schemaName")
}
