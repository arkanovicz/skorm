package com.republicate.skorm

import com.republicate.kson.Json

open class CoreProcessor(protected open val connector: Connector): Processor {

    override val configTag = "core"
    override val config = Configuration()

    private val queries = mutableMapOf<String, QueryDef>() // CB TODO - or concurrent?
    private val readFilters = mutableMapOf<String, Filter<*>>()
    private val writeFilters = mutableMapOf<String, Filter<Any?>>()

    // CB TODO - register() should make calls to define()
    override fun register(entity: Entity) {
        queries["${entity.path}/browse"] = SimpleQuery(entity.generateBrowseStatement())
        if (entity.primaryKey.isNotEmpty()) {
            queries["${entity.path}/delete"] = SimpleQuery(entity.generateDeleteStatement())
            queries["${entity.path}/fetch"] = SimpleQuery(entity.generateFetchStatement())
            queries["${entity.path}/insert"] = SimpleQuery(entity.generateInsertStatement())
            queries["${entity.path}/update"] = DynamicQuery {
                entity.generateUpdateStatement(it)
            }
        }
    }

    internal fun define(path: String, definition: QueryDef) {
        queries.put(path, definition)?.let {
            throw SkormException("attribute $path already defined")
        }
    }

    override fun initialize() {
        connector.initialize(connector.configTag?.let {config.getObject(it)})
    }


//
//    override suspend fun insert(instance: Instance): GeneratedKey? {
//        val pk = instance.entity.primaryKey
//        return if (pk.size == 1 && pk[0].generated)
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance, GeneratedKeyMarker(pk[0].sqlName))
//        else
//            connector.mutate(SqlUtils.getInsertStatement(instance.entity), instance)
//    }

    override suspend fun eval(path: String, params: Map<String, Any?>): Any? {
        val query = getSingleQuery(path, params.keys)
        val (names, it) = connector.query(query.stmt, *query.params.map { params[it] }.toTypedArray())
        if (names.size != 1) throw SkormException("scalar attribute $path expects only one column")
        if (!it.hasNext()) throw SkormException("scalar attribute $path has no result row")
        val ret = it.next()
        if (it.hasNext()) throw SkormException("scalar attribute $path has more than one result row")
        return ret
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Json.Object? {
        val query = getSingleQuery(path, params.keys)
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
        val query = getSingleQuery(path, params.keys)
        val (names, it) = connector.query(query.stmt, *query.params.map {
            params[it]
                ?:
                if (params.containsKey(it)) null
                else throw SkormException("Missing parameter: $it")
        }.toTypedArray())
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

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val queries = getMutationQueries(path, params.keys)
        if (queries.size > 1) {
            var totalChanged = 0L
            transaction {
                with (this as CoreProcessorTransaction) {
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
        return CoreProcessorTransaction(connector)
    }

    private inline fun getSingleQuery(path: String, params: Collection<String>) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }.queries(params).firstOrNull() ?: throw SkormException("single query excpected: $path")

    private inline fun getMutationQueries(path: String, params: Collection<String>) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }.queries(params)

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

class CoreProcessorTransaction(parentConnector: Connector) : CoreProcessor(parentConnector.begin()), Transaction {
    val txConnector: TransactionConnector get() = connector as TransactionConnector

    override suspend fun rollback() {
        txConnector.rollback()
    }

    override suspend fun commit() {
        txConnector.commit()
    }
}
