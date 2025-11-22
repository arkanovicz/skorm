package com.republicate.skorm.core

import com.republicate.kson.Json
import com.republicate.skorm.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("core")

@Suppress("NOTHING_TO_INLINE")
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

    // Identifiers mapping functors
    internal var readMapper: IdentifierMapper = identityMapper
    internal var writeMapper: IdentifierMapper = identityMapper

    // Values filters, indexed by sql type
    internal var readFilters = mutableMapOf<String, ValueFilter>()
    internal var writeFilters = mutableMapOf<String, ValueFilter>()

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

    override fun configure(cfg: Map<String, Any?>) {
        super.configure(cfg)
        config.getStrings("mapping.read")?.forEachIndexed { i, name ->
            val mapper = IdentifiersMapping[name]
            if (i == 0) {
                readMapper = mapper
            } else {
                readMapper = readMapper.compose(mapper)
            }
        }
        config.getStrings("mapping.write")?.forEachIndexed { i, name ->
            val mapper = IdentifiersMapping[name]
            if (i == 0) {
                writeMapper = mapper
            } else {
                writeMapper = writeMapper.compose(mapper)
            }
        }
        config.getObject("filter.read")?.entries?.forEach {
            val sqlType = it.key
            val filterName = it.value as String
            val filter = ValuesFiltering[filterName]
            readFilters[sqlType] = filter
        }
        config.getObject("filter.write")?.entries?.forEach {
            val sqlType = it.key
            val filterName = it.value as String
            val filter = ValuesFiltering[filterName]
            writeFilters[sqlType] = filter
        }
    }

    override fun initialize() {
        connector.initialize(connector.configTag?.let {
            config.getObject(it)
        })
        // provide default values for identifiers mappers based on meta infos
        // CB TODO - we may not want this, and require an explicit mapping
        if (readMapper == identityMapper) {
            readMapper = IdentifiersMapping.snakeToCamel
        }
        if (writeMapper == identityMapper) {
            writeMapper =  when (identifierInternalCase) {
                'U' -> {{ "$identifierQuoteChar${it.uppercase()}$identifierQuoteChar" }}
                'L' -> {{ "$identifierQuoteChar${it.lowercase()}$identifierQuoteChar" }}
                else -> { identityMapper }
            }
            writeMapper = writeMapper.compose(IdentifiersMapping.camelToSnake)
        }
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

    override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?): Json.Object? {
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
                if (this is Instance) {
                    putNamesValues(names, rawValues)
                    setClean()
                } else {
                    putAll(names, rawValues)
                }
            }
        }
    }

    override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?): Sequence<Json.Object> {
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
                    if (this is Instance) {
                        putNamesValues(names, it)
                        setClean()
                    } else {
                        putAll(names, it)
                    }
                }
            }
        }
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val (schema, queries) = getMutationQueries(path, params.keys.filter { it !== GeneratedKeyMarker.PARAM_KEY })
        if (queries.size > 1) {
            var totalChanged = 0L
            transaction(schema) {
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

    override suspend fun begin(schema: String): Transaction {
        return CoreProcessorTransaction(connector, schema)
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
            put(readMapper(name), value)
        }
    }

    private fun Instance.putNamesValues(names: Array<String>, values: Array<Any?>) {
        putRawFields(names.zip(values).toMap())
    }

    override fun downstreamMapping(name: String) = readMapper(name)

    override fun upstreamMapping(name: String) = writeMapper(name)

    override fun downstreamFilter(type: String, value: Any?) =
        readFilters[type]?.let { filter -> filter(value) } ?: value

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
            primaryKey.joinToString(" AND ") { "${writeMapper(it.name)} = ${it.parameter()}" }
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }

    private fun Entity.generateInsertStatement(params: Collection<String>): QueryDefinition {
        val names = params.joinToString(",") { writeMapper(it) }
        val values = Array(params.size) { "?" }.joinToString(",")
        val stmt = "INSERT INTO ${schema.name}.${writeMapper(name)} ($names) VALUES ($values);"
        var queryParams = params.toMutableList()
        if (primaryKey.size == 1 && primaryKey.first().isGenerated) {
            queryParams.add(GeneratedKeyMarker.PARAM_KEY)
        }
        return QueryDefinition(stmt, queryParams)
    }

    private fun Entity.generateDeleteStatement(): QueryDefinition {
        val stmt = "DELETE FROM ${schema.name}.${writeMapper(name)} WHERE ${
            primaryKey.joinToString(" AND ") { "${writeMapper(it.name)} = ${it.parameter()}" }
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }

    private fun Entity.generateUpdateStatement(params: Collection<String>): QueryDefinition {
        val stmt = "UPDATE ${schema.name}.${writeMapper(name)} SET ${
            params.joinToString(", ") { "${writeMapper(it)} = ?" }
        } WHERE ${
            primaryKey.joinToString(" AND ") { "${writeMapper(it.name)} = ${it.parameter()}" }
        };"
        return QueryDefinition(stmt, primaryKey.map { it.name })
    }

    private fun Field.parameter(): String {
        return connector.getMetaInfos().let { meta ->
            when {
                meta.strictColumnTypes && meta.columnMarkers -> "?::${this.type}"
                meta.strictColumnTypes -> "CAST(? AS ${this.type})"
                else -> "?"
            }
        }
    }

    override fun close() {
        connector.close()
    }
}

class CoreProcessorTransaction(parentConnector: Connector, schema: String) : CoreProcessor(parentConnector.begin(schema)), Transaction {
    private val txConnector: TransactionConnector get() = connector as TransactionConnector

    override suspend fun rollback() {
        txConnector.rollback()
    }

    override suspend fun commit() {
        txConnector.commit()
    }
}
