package com.republicate.skorm.core

import com.republicate.kson.Json
import com.republicate.skorm.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger("core")

open class CoreProcessor(protected open val connector: Connector): Processor {

    override val configTag = "core"
    override val config = Configuration()
    override val restMode = false

    private val queries = mutableMapOf<String, AttributeDefinition>() // CB TODO - or concurrent?
    // TODO
//    private val readFilters = mutableMapOf<String, Mapper<*>>()
//    private val writeFilters = mutableMapOf<String, Mapper<Any?>>()

    private val identifierQuoteChar: Char by lazy { connector.getMetaInfos().identifierQuoteChar }
    private val identifierInternalCase: Char by lazy { connector.getMetaInfos().identifierInternalCase }

    // var readMapper: IdentifierMapper = identityMapper UNUSED
    internal var writeMapper: IdentifierMapper = identityMapper

    private fun register(path: String, query: AttributeDefinition) {
        logger.trace { "registering $path to $query" }
        queries[path] = query
    }

    // CB TODO - register() should make calls to define()
    override fun register(entity: Entity) {
        register("${entity.path}/browse", SimpleQuery(entity.schema.name, entity.generateBrowseStatement()))
        register("${entity.path}/insert", DynamicQuery(entity.schema.name) {
            entity.generateInsertStatement(it)
        })
        if (entity.primaryKey.isNotEmpty()) {
            register("${entity.path}/delete", SimpleQuery(entity.schema.name, entity.generateDeleteStatement()))
            register("${entity.path}/fetch", SimpleQuery(entity.schema.name, entity.generateFetchStatement()))
            register("${entity.path}/update", DynamicQuery(entity.schema.name) {
                entity.generateUpdateStatement(it)
            })
        }
    }

    fun define(path: String, definition: AttributeDefinition) {
        logger.trace { "defining $path to $definition" }
        queries.put(path, definition)?.let {
            throw SkormException("attribute $path already defined")
        }
    }

    override fun initialize() {
        connector.initialize(connector.configTag?.let { config.getObject(it) })
        writeMapper =  when (identifierInternalCase) {
            'U' -> {{ "$identifierQuoteChar${it.uppercase()}$identifierQuoteChar" }}
            'L' -> {{ "$identifierQuoteChar${it.lowercase()}$identifierQuoteChar" }}
            else -> { identityMapper }
        }
        writeMapper = writeMapper.compose(camelToSnake)
    }

    override suspend fun eval(path: String, params: Map<String, Any?>): Any? {
        val (schema, query) = getSingleQuery(path, params.keys)
        val (names, it) = connector.query(schema, query.stmt, *query.params.map { params[it] }.toTypedArray())
        if (names.size != 1) throw SkormException("scalar attribute $path expects only one column")
        if (!it.hasNext()) throw SkormException("scalar attribute $path has no result row")
        val ret = it.next()
        if (it.hasNext()) throw SkormException("scalar attribute $path has more than one result row")
        return ret
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: InstanceFactory?): Json.Object? {
        val (schema, query) = getSingleQuery(path, params.keys)
        val (names, it) = connector.query(schema, query.stmt, *query.params.map { params[it] }.toTypedArray())
        if (!it.hasNext()) return null // CB TODO - non-null result should be specifiable
        val rawValues = it.next()
        if (it.hasNext()) throw SkormException("raw attribute $path has more than one result row") // CB TODO - could be relaxed by config
        return when (factory) {
            null -> Json.MutableObject().apply {
                putAll(names, rawValues)
            }
            else -> factory().apply {
                putNamesValues(names, rawValues)
                setClean()
            }
        }
    }

    override suspend fun query(path: String, params: Map<String, Any?>, factory: InstanceFactory?): Sequence<Json.Object> {
        val (schema, query) = getSingleQuery(path, params.keys)
        val (names, it) = connector.query(schema, query.stmt, *query.params.map {
            params[it]
                ?:
                if (params.containsKey(it)) null
                else throw SkormException("Missing parameter: $it")
        }.toTypedArray())
        return it.asSequence().map {
            when (factory) {
                null -> Json.MutableObject().apply {
                    putAll(names, it)
                }
                else -> factory().apply {
                    putNamesValues(names, it)
                    setClean()
                }
            }
        }
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val (schema, queries) = getMutationQueries(path, params.keys.filter { it !== GeneratedKeyMarker.PARAM_KEY })
        if (queries.size > 1) {
            var totalChanged = 0L
            transaction {
                with (this as CoreProcessorTransaction) {
                    for (query in queries) {
                        totalChanged += connector.mutate(schema, query.stmt, *query.params.map { params[it] }.toTypedArray())
                    }
                }
            }
            return totalChanged
        } else {
            val query = queries.first()
//            return connector.mutate(query.stmt, *query.params.map { params[it] }.toMutableList().also { list ->
//                if (query.params.isNotEmpty() && query.params.last() === GeneratedKeyMarker.PARAM_KEY) {
//                    list.add(GeneratedKeyMarker.PARAM_KEY)
//                }
//            }.toTypedArray())
            return connector.mutate(schema, query.stmt, *query.params.map { params[it] }.toTypedArray())
        }
    }

    override suspend fun begin(): Transaction {
        return CoreProcessorTransaction(connector)
    }

    private inline fun getSingleQuery(path: String, params: Collection<String>) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }.let {
        Pair(it.schema,
            it.queries(params).firstOrNull() ?: throw SkormException("single query expected: $path"))
    }

    private inline fun getMutationQueries(path: String, params: Collection<String>) = queries.getOrElse(path) {
        throw SkormException("attribute not found: $path")
    }.let {
        Pair(it.schema, it.queries(params))
    }

    private fun Json.MutableObject.putAll(names: Array<String>, values: Array<Any?>) {
        names.zip(values).forEach { (name, value) ->
            put(name, value)
        }
    }

    private fun Instance.putNamesValues(names: Array<String>, values: Array<Any?>) {
        names.zip(values).forEach { (name, value) ->
            // TODO - review
            putRawValue(name.lowercase(), value)
        }
    }

    // sql utils
    private fun Entity.generateBrowseStatement(): QueryDefinition {
        val stmt = "SELECT ${
            fields.values.joinToString(", ") { writeMapper(it.name) }
        } FROM ${schema.name}.${writeMapper(name)};"
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
        var queryParams = params.toMutableList()
        if (primaryKey.size == 1 && primaryKey.first().generated) {
            queryParams.add(GeneratedKeyMarker.PARAM_KEY)
        }
        return QueryDefinition(stmt, queryParams)
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
