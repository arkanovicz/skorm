package com.republicate.skorm

open class Entity(name: String, schema: Schema): AttributeHolder(name, schema) {
    internal val processor = AttributeProcessor(this)
    val schema get() = parent as Schema

    private val _fields = mutableMapOf<String, Field>()
    val fields: Map<String, Field> by _fields

    var factory: () -> Instance = { Instance(this) }

    internal fun insert(instance: Instance) {

    }
    internal fun update(instance: Instance) {

    }
    internal fun delete(instance: Instance) {

    }

    fun fetch(vararg key: Any) {

    }

    fun iterator(): Iterator<Instance> {
        TODO("todo")
    }
}
