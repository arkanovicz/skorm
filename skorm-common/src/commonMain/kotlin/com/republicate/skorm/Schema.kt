package com.republicate.skorm

open class Schema(name: String, database: Database) : AttributeHolder(name, database) {
    val database: Database
        get() = parent as Database

    private val entities = mutableMapOf<String, Entity>()
    fun getEntity(entityName: String) = entities[entityName] ?: throw SQLException("unknown entity: $entityName")

}
