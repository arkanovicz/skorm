package com.republicate.skorm

import com.republicate.kson.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger("core")

open class CoreProcessor(protected open val connector: Connector): Processor {

    override val configTag = "core"
    override val config = Configuration()

    private val queries = mutableMapOf<String, Query>() // CB TODO - or concurrent?
    // TODO
//    private val readFilters = mutableMapOf<String, Mapper<*>>()
//    private val writeFilters = mutableMapOf<String, Mapper<Any?>>()

    private val identifierQuoteChar: Char by lazy { connector.metaInfos.identifierQuoteChar }
    private val identifierInternalCase: Char by lazy { connector.metaInfos.identifierInternalCase }

    // var readMapper: IdentifierMapper = identityMapper UNUSED
    internal var writeMapper: IdentifierMapper = identityMapper

    private fun register(path: String, query: Query) {
        logger.trace { "registering $path to $query" }
        queries[path] = query
    }

    // CB TODO - register() should make calls to define()
    override fun register(entity: Entity) {
        register("${entity.path}/browse", SimpleQuery(entity.generateBrowseStatement()))
        if (entity.primaryKey.isNotEmpty()) {
            register("${entity.path}/delete", SimpleQuery(entity.generateDeleteStatement()))
            register("${entity.path}/fetch", SimpleQuery(entity.generateFetchStatement()))
            register("${entity.path}/insert", DynamicQuery {
                entity.generateInsertStatement(it)
            })
            register("${entity.path}/update", DynamicQuery {
                entity.generateUpdateStatement(it)
            })
        }
    }

    fun define(path: String, definition: Query) {
        queries.put(path, definition)?.let {
            throw SkormException("attribute $path already defined")
        }
    }

    override fun initialize() {
        connector.initialize(connector.configTag?.let { config.getObject(it) })
        writeMapper = when (identifierInternalCase) {
            'U' -> {{ "$identifierQuoteChar${it.uppercase()}$identifierQuoteChar" }}
            'L' -> {{ "$identifierQuoteChar${it.lowercase()}$identifierQuoteChar" }}
            else -> { identityMapper }
        }
    }

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
            // TODO
//            val filterKey = "${entity.path}/$name"
//            put(name, writeFilters[filterKey]?.apply(value) ?: value)
            put(name, value)
        }
    }

    // sql utils
    private fun Entity.generateBrowseStatement(): QueryDefinition {
        val stmt = "SELECT ${
            fields.values.joinToString(", ") { writeMapper(it.name) }
        } FROM ${schema.name}.$name;"
        return QueryDefinition(stmt, emptyList())
    }

    private fun Entity.generateFetchStatement(): QueryDefinition {
        val stmt = "SELECT ${
            fields.values.joinToString(", ") { writeMapper(it.name) }
        } FROM ${schema.name}.$name WHERE ${
            primaryKey.joinToString(" AND ") { "${writeMapper(it.name)} = ?" }
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }

    private fun Entity.generateInsertStatement(params: Collection<String>): QueryDefinition {
        val names = params.joinToString(",") { writeMapper(it) }
        val values = Array(params.size) { "?" }.joinToString(",")
        val stmt = "INSERT INTO ${schema.name}.${writeMapper(name)} ($names) VALUES ($values);"
        return QueryDefinition(stmt, params.toList())
    }

    private fun Entity.generateDeleteStatement(): QueryDefinition {
        val stmt = "DELETE FROM ${schema.name}.${writeMapper(name)} WHERE ${
            primaryKey.joinToString(" AND ") { "${writeMapper(it.name)} = ?" }
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }

    private fun Entity.generateUpdateStatement(params: Collection<String>): QueryDefinition {
        val stmt = "UPDATE ${schema.name}.${writeMapper(name)} SET ${
            params.joinToString(", ") { "${writeMapper(it)} = ?" }
        } WHERE ${
            primaryKey.map { "${writeMapper(it.name)} = ?" }.joinToString(" AND ")
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }
}

class CoreProcessorTransaction(parentConnector: Connector) : CoreProcessor(parentConnector.begin()), Transaction {
    private val txConnector: TransactionConnector get() = connector as TransactionConnector

    override suspend fun rollback() {
        txConnector.rollback()
    }

    override suspend fun commit() {
        txConnector.commit()
    }
}
