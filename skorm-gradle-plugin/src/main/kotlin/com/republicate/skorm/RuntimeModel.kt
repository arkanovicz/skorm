package com.republicate.skorm.model

import com.republicate.kddl.ASTTable

class RMDatabase(val name: String) {
    val schemas = mutableListOf<RMSchema>()
}

class RMSchema(val name: String) {
    val items = mutableListOf<RMItem>()
}

class RMItem(val name: String) {
    // parsed infos

    // entity of instances on which this attribute is defined
    var receiver: String? = null
    // manually specified arguments
    var arguments : Set<Pair<String, String>>? = null
    // whether the item is an action
    var action: Boolean = false
    //  item type
    var type: RMType? = null
    // whether the item result is nullable
    var nullable: Boolean = false
    // whether the item result is a collection
    var multiple: Boolean = false
    // whether the item is a transaction
    var transaction: Boolean = false
    // the sql for this item
    var sql: String? = null

    // structure infos

    // the parameters needed to execute this item (as found in the SQL query)
    val parameters = mutableSetOf<String>()
}

sealed class RMType(val name: String) {
    abstract fun isComposite(): Boolean
}

class RMSimpleType(name: String, val entity: RMEntityRef? = null): RMType(name) {
    val isEntity get() = entity != null
    override fun isComposite() = false
}

/** an entity type as written: [schema] null means the block's own */
data class RMEntityRef(val schema: String?, val table: String) {
    override fun toString() = schema?.let { "$it.$table" } ?: table
}

class RMCompositeType(name: String): RMType(name) {
    override fun isComposite() = true
    var parent: RMEntityRef? = null
    val fields = mutableListOf<RMField>()
}


class RMField(val name: String, val type: String, val nullable: Boolean)

