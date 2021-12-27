package com.republicate.skorm

typealias GeneratedKey = Long
typealias Affected = Long
typealias RowSet = Sequence<Instance>

interface Processor {
    fun connect(connector: Connector)

    // instances
    fun insert(instance: Instance): GeneratedKey?
    fun update(instance: Instance)
    fun upsert(instance: Instance)
    fun delete(instance: Instance)

    // attributes
    fun eval(path: String, vararg params: Any?): Instance?
    fun retrieve(path: String, result: Entity?, vararg params: Any?): Instance?
    fun query(path: String, result: Entity?, vararg params: Any?): RowSet
    fun perform(path: String, vararg params: Any?): Affected
    fun attempt(path: String, vararg params: Any?): List<Affected>

    // transaction
    fun begin()
    fun savePoint(name: String)
    fun rollback(savePoint: String?)
    fun commit()

}

