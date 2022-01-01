package com.republicate.skorm

open class Schema(name: String, database: Database) : AttributeHolder(name, database) {
    val database: Database
        get() = parent as Database

    private val _entities = mutableMapOf<String, Entity>()
    val entities: Map<String, Entity> by _entities

}
