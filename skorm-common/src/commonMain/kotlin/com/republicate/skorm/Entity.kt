package com.republicate.skorm

import com.republicate.kson.Json

open class Entity<in INST: Entity<INST>.Instance>(name: String, schema: Schema): AttributeHolder(name, schema) {
    protected val processor = AttributeProcessor(this)
    val schema get() = parent as Schema

    private val _fields = mutableMapOf<String, Field>()
    val fields: Map<String, Field> by _fields

    var factory: () -> INST = { Instance() as INST }

    internal fun insert(instance: INST) {

    }
    internal fun update(instance: INST) {

    }
    internal fun delete(instance: INST) {

    }

    fun fetch(vararg key: Any) {

    }

    fun iterator(): Iterator<Instance> {
        TODO("todo")
    }

    open inner class Instance() : Json.Object(), ExposesAttributes {
//        companion object
        val dirtyFields = BitSet(MAX_FIELDS) // init size needed, see below
        var persisted = false

        // instance mutations

        fun insert() {
            if (persisted) throw SQLException("cannot insert a persisted instance")
            insert(this as INST)
            persisted = true
            setClean()
        }

        fun update() {
            if (!persisted) throw SQLException("cannot update a volatile instance")
            update(this as INST)
            setClean()
        }

        fun upsert() = if (persisted) update() else delete()

        fun delete() {
            if (!persisted) throw SQLException("cannot delete a volatile instance")
            delete(this as INST)
            persisted = false
        }

        // dirty flags handling
        private fun setClean() {
            dirtyFields
        }

        override fun eval(attrName: String, vararg params: Any?) = processor.eval(attrName, this, *params)
        override fun retrieve(attrName: String, vararg params: Any?) = processor.retrieve(attrName, this, *params)
        override fun query(attrName: String, vararg params: Any?) = processor.query(attrName, this, *params)
        override fun perform(attrName: String, vararg params: Any?) = processor.perform(attrName, this, *params)
        override fun attempt(attrName: String, vararg params: Any?) = processor.attempt(attrName, *params)
    }
}
