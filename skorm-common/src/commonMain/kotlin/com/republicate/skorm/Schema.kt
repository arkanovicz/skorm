package com.republicate.skorm

open class Schema(name: String, database: Database) : AttributeHolder(name, database) {
    val database: Database
        get() = parent as Database

    val entities = mutableMapOf<String, Entity>()

}
