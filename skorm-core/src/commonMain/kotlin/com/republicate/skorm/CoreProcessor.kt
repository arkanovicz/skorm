package com.republicate.skorm

import com.republicate.kson.Json

open class CoreProcessor(protected open val connector: Connector): Processor {

    override val configTag = "core"
    override val config = Configuration()

    override fun initialize() {
        connector.initialize(connector.configTag?.let {config.getObject(it)})
    }

    private val queries = mutableMapOf<String, List<Query>>()
    private val readFilters = mutableMapOf<String, Filter<*>>()
    private val writeFilters = mutableMapOf<String, Filter<Any?>>()

    private val connectors = ConcurrentMap<String, Connector>()

//
//    override suspend fun insert(instance: Instance): GeneratedKey? {
//        val pk = instance.entity.primaryKey
//        return if (pk.size == 1 && pk[0].generated)
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance, GeneratedKeyMarker(pk[0].sqlName))
//        else
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance)
//    }

    override suspend fun eval(path: String, params: Map<String, Any?>): Any? {
        val query = getSingleQuery(path)
        val (names, it) = connector.query(query.stmt, *query.params.map { params[it] }.toTypedArray())
        if (names.size != 1) throw SkormException("scalar attribute $path expects only one column")
        if (!it.hasNext()) throw SkormException("scalar attribute $path has no result row")
        val ret = it.next()
        if (it.hasNext()) throw SkormException("scalar attribute $path has more than one result row")
        return ret
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Json.Object? {
        val query = getSingleQuery(path)
        val (names, it) = connector.query(query.stmt, *query.params.map { params[it] }.toTypedArray())
        if (!it.hasNext()) return null // CB TODO - non-null result should be specifiable
        val rawValues = it.next()
        if (it.hasNext()) throw SkormException("raw attribute $path has more than one result row") // CB TODO - could be relaxed by config
        return when (result) {
            null -> Json.MutableObject().apply {
                putAll(names, rawValues)
            }
            else -> result.new().apply {
                putInternal(names, rawValues)
            }
        }
    }

    override suspend fun query(path: String, params: Map<String, Any?>, result: Entity?): Sequence<Json.Object> {
        val query = getSingleQuery(path)
        val (names, it) = connector.query(query.stmt, *query.params.map { params[it] }.toTypedArray())
        return it.asSequence().map {
            when (result) {
                null -> Json.MutableObject().apply {
                    putAll(names, it)
                }
                else -> result.new().apply {
                    putInternal(names, it)
                }
            }
        }
    }

//    private fun getUpdateQueryWhereClause(path: String, params: Map<String, Any?>): String {
//        val keySuffix = params.keys.sorted().joinToString("/")
//        return queries.getOrPut("$path/$keySuffix") {
//            val whereClause = params.keys.joinToString(", ") {
//                "$it=?"
//            }
//            // Nope... we need sql names! CB TODO
//            "UPDATE ... SET $whereClause WHERE ..."
//        }
//    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val queries = getMutationQueries(path)
        if (queries.size > 1) {
            var totalChanged = 0L
            transaction {
                with (this as TransactionCoreProcessor) {
                    for (query in queries) {
                        totalChanged += connector.mutate(query.stmt, *query.params.map { params[it] }.toTypedArray())
                    }
                }
            }
            return totalChanged
        } else {
            val query = queries.first()
            return connector.mutate(query.stmt, *query.params.map { params[it] }.toTypedArray())
        }
    }

    override suspend fun begin(): Transaction {
        TODO("Not yet implemented")
    }

    private inline fun getSingleQuery(path: String) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }.firstOrNull() ?: throw SkormException("single query excpected: $path")

    private inline fun getMutationQueries(path: String) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }

    private fun Json.MutableObject.putAll(names: Array<String>, values: Array<Any?>) {
        names.zip(values).forEach { (name, value) ->
            put(name, value)
        }
    }

    private fun Instance.putInternal(names: Array<String>, values: Array<Any?>) {
        names.zip(values).forEach { (name, value) ->
            val filterKey = "${entity.path}/$name"
            put(name, writeFilters[filterKey]?.apply(value) ?: value)
        }
    }
}

class TransactionCoreProcessor(parentConnector: Connector) : CoreProcessor(parentConnector.begin()), Transaction {
    val txConnector: TransactionConnector get() = connector as TransactionConnector

    override suspend fun rollback() {
        txConnector.rollback()
    }

    override suspend fun commit() {
        txConnector.commit()
    }
}
