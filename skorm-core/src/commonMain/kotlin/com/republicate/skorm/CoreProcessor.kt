package com.republicate.skorm

open class CoreProcessor(val connectorFactory: ConnectorFactory): Processor {

    protected val queries = mutableMapOf<String, String>()
    private val readFilters = mutableMapOf<String, Filter<*>>()
    private val writeFilters = mutableMapOf<String, Filter<Any?>>()

    private val connectors = ConcurrentMap<String, Connector>()

    private fun getConnector(path: String): Connector {
        val lastSep = path.lastIndexOf('/')
        check(lastSep > 1)
        val key = path.substring(0, lastSep - 1)
        return connectors.getOrPut(key) {
            connectorFactory.connect()
        }
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
        val (names, it) = getConnector(path).query(getQuery(path), *params.values.toTypedArray())
        if (names.size != 1) throw SkormException("scalar attribute $path expects only one column")
        if (!it.hasNext()) throw SkormException("scalar attribute $path has no result row")
        val ret = it.next()
        if (it.hasNext()) throw SkormException("scalar attribute $path has more than one result row")
        return ret
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Instance? {
        val (names, it) = getConnector(path).query(getQuery(path), *params.values.toTypedArray())
        if (!it.hasNext()) return null
        val ret = result?.new() ?: voidEntity.new()
        val rawValues = it.next()
        if (it.hasNext()) throw SkormException("raw attribute $path has more than one result row")
        ret.putInternal(names, rawValues)
        return ret
    }

    override suspend fun query(path: String, params: Map<String, Any?>, result: Entity?): Sequence<Instance> {
        val (names, it) = getConnector(path).query(getQuery(path), *params.values.toTypedArray())
        return it.asSequence().map {
            (result?.new() ?: voidEntity.new()).apply {
                putInternal(names, it)
            }
        }
    }

    private fun getUpdateQueryWhereClause(path: String, params: Map<String, Any?>): String {
        val keySuffix = params.keys.sorted().joinToString("/")
        return queries.getOrPut("$path/$keySuffix") {
            val whereClause = params.keys.joinToString(", ") {
                "$it=?"
            }
            // Nope... we need sql names! CB TODO
            "UPDATE ... SET $whereClause WHERE ..."
        }
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val query =
            if (path.endsWith("/update")) getUpdateQueryWhereClause(path, params)
            else getQuery(path)
        return getConnector(path).mutate(getQuery(path), *params.values.toTypedArray())
    }

    override suspend fun attempt(path: String, params: Map<String, Any?>): List<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun begin(): Transaction {
        TODO("Not yet implemented")
    }

    private inline fun getQuery(path: String) = queries.getOrElse(path) {
        throw SkormException("row attribute not found: $path")
    }

    private fun Instance.putInternal(names: Array<String>, values: Array<Any?>) {
        names.zip(values).forEach { (name, value) ->
            val filterKey = "${entity.path}/$name"
            put(name, writeFilters[filterKey]?.apply(value) ?: value)
        }
    }
}
