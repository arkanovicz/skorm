package com.republicate.skorm

import com.republicate.kson.Json

open class Instance(val entity: Entity) : Json.Object(), ExposesAttributes {
    companion object
    val dirtyFields = BitSet(MAX_FIELDS) // init size needed, see below
    var persisted = false

    // instance mutations

    fun insert() {
        if (persisted) throw SQLException("cannot insert a persisted instance")
        entity.insert(this)
        persisted = true
        setClean()
    }

    fun update() {
        if (!persisted) throw SQLException("cannot update a volatile instance")
        entity.update(this)
        setClean()
    }

    fun upsert() = if (persisted) update() else delete()
    
    fun delete() {
        if (!persisted) throw SQLException("cannot delete a volatile instance")
        entity.delete(this)
        persisted = false
    }

    // dirty flags handling
    private fun setClean() {
        dirtyFields
    }

    override fun eval(attrName: String, vararg params: Any?) = entity.processor.eval(attrName, this, *params)
    override fun retrieve(attrName: String, vararg params: Any?) = entity.processor.retrieve(attrName, this, *params)
    override fun query(attrName: String, vararg params: Any?) = entity.processor.query(attrName, this, *params)
    override fun perform(attrName: String, vararg params: Any?) = entity.processor.perform(attrName, this, *params)
    override fun attempt(attrName: String, vararg params: Any?) = entity.processor.attempt(attrName, this, *params)

}

