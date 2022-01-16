package com.republicate.skorm

class CoreProcessor(val connector: Connector): Processor {

    // CB TODO - concurrency
    private val queries = mutableMapOf<String, String>()

//
//    override suspend fun insert(instance: Instance): GeneratedKey? {
//        val pk = instance.entity.primaryKey
//        return if (pk.size == 1 && pk[0].generated)
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance, GeneratedKeyMarker(pk[0].sqlName))
//        else
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance)
//    }
    override suspend fun eval(path: String, params: Map<String, Any?>): Any? {
        val qry = queries.getOrElse(path) {
            throw SQLException("scalar attribute not found: $path")
        }
        val (names, it) = connector.query(qry, *params.values.toTypedArray())
        if (names.size != 1) throw SQLException("scalar attribute $path expects only one column")
        if (!it.hasNext()) throw SQLException("scalar attribute $path has no result row")
        val ret = it.next()
        if (it.hasNext()) throw SQLException("scalar attribute $path has more than one result row")
        return ret
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Instance? {
        TODO("Not yet implemented")
    }

    override suspend fun query(path: String, params: Map<String, Any?>, result: Entity?): Sequence<Instance> {
        TODO("Not yet implemented")
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        TODO("Not yet implemented")
    }

    override suspend fun attempt(path: String, params: Map<String, Any?>): List<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun begin(): Transaction {
        TODO("Not yet implemented")
    }

}
