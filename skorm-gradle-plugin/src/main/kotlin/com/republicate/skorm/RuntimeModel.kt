package com.republicate.skorm.model

class RMDatabase(val name: String) {
    val schemas = mutableListOf<RMSchema>()
}

class RMSchema(val name: String) {
    val items = mutableListOf<RMItem>()
}

class RMItem(val name: String) {
    var receiver: String? = null
    val arguments = mutableSetOf<String>()
    var action: Boolean = false
    var type: RMType? = null
    var nullable: Boolean = false
    var multiple: Boolean = false
    var transaction: Boolean = false
    var sql: String? = null

}

sealed class RMType(val name: String) {
    abstract fun isComposite(): Boolean
}

class RMSimpleType(name: String, val isEntity: Boolean): RMType(name) {
    override fun isComposite() = false
}

class RMCompositeType(name: String): RMType(name) {
    override fun isComposite() = true
    var parent: String? = null
    val fields = mutableListOf<RMField>()
}


class RMField(val name: String, val type: String)

