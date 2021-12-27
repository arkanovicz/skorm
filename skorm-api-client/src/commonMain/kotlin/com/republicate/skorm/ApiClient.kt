package com.republicate.skorm

class ApiClient : Processor {
    override fun connect(connector: Connector) {
        TODO("Not yet implemented")
    }

    override fun insert(instance: Instance): GeneratedKey? {
        TODO("Not yet implemented")
    }

    override fun update(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun upsert(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun delete(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun eval(path: String, vararg params: Any?): Instance? {
        TODO("Not yet implemented")
    }

    override fun retrieve(path: String, result: Entity?, vararg params: Any?): Instance? {
        TODO("Not yet implemented")
    }

    override fun query(path: String, result: Entity?, vararg params: Any?): RowSet {
        TODO("Not yet implemented")
    }

    override fun perform(path: String, vararg params: Any?): Int {
        TODO("Not yet implemented")
    }

    override fun attempt(path: String, vararg params: Any?): List<Int> {
        TODO("Not yet implemented")
    }

    override fun begin() {
        TODO("Not yet implemented")
    }

    override fun savePoint(name: String) {
        TODO("Not yet implemented")
    }

    override fun rollback(savePoint: String?) {
        TODO("Not yet implemented")
    }

    override fun commit() {
        TODO("Not yet implemented")
    }
}