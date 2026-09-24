@file:OptIn(SkormInternalApi::class)

package com.republicate.skorm.core

import com.republicate.kson.Json
import com.republicate.skorm.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads

private val logger = KotlinLogging.logger("core")

@Suppress("NOTHING_TO_INLINE")
const val KIND_COLUMN = "kind"

/**
 * Runs attributes on [connector]; the reads of a read-only database run on [readConnector] instead, the same
 * connector by default — a separate one, on a SELECT-only role, is what makes a read-only database actually so.
 * A store's read-only and mutable databases share one processor: attributes are registered by path *and*
 * mutability, so both register the same reads and only the mutable one registers writes.
 */
open class CoreProcessor @JvmOverloads constructor(
    protected open val connector: Connector,
    protected open val readConnector: Connector = connector,
    /** where the connector's blocking calls run, so that the calling coroutine's thread never does */
    val blockingContext: CoroutineContext = ioContext
): Processor {

    private suspend fun <T> blocking(block: () -> T): T = withContext(blockingContext) { block() }

    override val configTag = "core"
    override val config = Configuration()
    override val restMode = false

    internal var queries = mutableMapOf<Pair<String, Boolean>, AttributeDefinition>() // CB TODO - or concurrent?
    private var initialized = false

    private fun connectorFor(mutable: Boolean) = if (mutable) connector else readConnector
    // TODO
//    private val readFilters = mutableMapOf<String, Mapper<*>>()
//    private val writeFilters = mutableMapOf<String, Mapper<Any?>>()

    private val identifierQuoteChar: Char by lazy { connector.getMetaInfos().identifierQuoteChar }
    private val identifierInternalCase: Char by lazy { connector.getMetaInfos().identifierInternalCase }

    // Identifiers mapping functors
    internal var readMapper: IdentifierMapper = identityMapper
    internal var writeMapper: IdentifierMapper = identityMapper

    // Values filters, indexed by sql type (defaults for json/jsonb)
    @Suppress("USELESS_ELVIS")
    internal var readFilters = mutableMapOf<String, ValueFilter>(
        "json" to (ValuesFiltering["parseJson"] ?: identityFilter),
        "jsonb" to (ValuesFiltering["parseJson"] ?: identityFilter),
        "JSON" to (ValuesFiltering["parseJson"] ?: identityFilter)  // H2
    )
    internal var writeFilters = mutableMapOf<String, ValueFilter>()

    private fun register(path: String, mutable: Boolean, query: AttributeDefinition) {
        logger.trace { "registering $path (mutable = $mutable) to $query" }
        queries[Pair(path, mutable)] = query
    }

    // CB TODO - register() should make calls to define()
    override fun register(entity: Entity) {
        val mutable = entity is MutableEntity
        register("${entity.path}/browse", mutable, SimpleQuery(entity.schema.name, entity.generateBrowseStatement()))
        if (entity.primaryKey.isNotEmpty()) {
            register("${entity.path}/fetch", mutable, SimpleQuery(entity.schema.name, entity.generateFetchStatement()))
        }
        if (!mutable) return
        register("${entity.path}/insert", true, DynamicQuery(entity.schema.name) {
            entity.generateInsertStatement(it)
        })
        if (entity.primaryKey.isNotEmpty()) {
            register("${entity.path}/delete", true, SimpleQuery(entity.schema.name, entity.generateDeleteStatement()))
            register("${entity.path}/update", true, DynamicQuery(entity.schema.name) {
                entity.generateUpdateStatement(it)
            })
        }
    }

    fun define(path: String, definition: AttributeDefinition, mutable: Boolean) {
        logger.trace { "defining $path (mutable = $mutable) to $definition" }
        queries.put(Pair(path, mutable), definition)?.let {
            throw SkormException("attribute $path already defined")
        }
    }

    override fun configure(cfg: Map<String, Any?>) {
        if (initialized) throw SkormException("processor already initialized") // the mappers are live: no reconfiguration through a handed-out database
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
        if (initialized) return // the store's second database initializes the same processor
        initialized = true
        connector.initialize(connector.configTag?.let { config.getObject(it) })
        // the read connector takes its own settings from `read.<tag>`, or the same ones
        if (readConnector !== connector) readConnector.initialize(readConnector.configTag?.let {
            config.getObject("read")?.getObject(it) ?: config.getObject(it)
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

    override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? {
        val (schema, query) = getSingleQuery(path, mutable, params.keys)
        val values = query.params.map { params[it] }.toTypedArray()
        return blocking {
            connectorFor(mutable).query(schema, query.stmt, *values).use { (names, it) ->
                if (names.size != 1) throw SkormException("scalar attribute $path expects only one column")
                // No result row -> null (indistinguishable from a row with a NULL value for nullable scalars)
                if (!it.hasNext()) return@use null
                val row = it.next()
                if (it.hasNext()) throw SkormException("scalar attribute $path has more than one result row")
                row[0]
            }
        }
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Row? {
        val (schema, query) = getSingleQuery(path, mutable, params.keys)
        val values = query.params.map { params[it] }.toTypedArray()
        return blocking {
            connectorFor(mutable).query(schema, query.stmt, *values).use { (names, it, types) ->
                if (!it.hasNext()) return@use null // CB TODO - non-null result should be specifiable
                val rawValues = it.next()
                if (it.hasNext()) throw SkormException("raw attribute $path has more than one result row") // CB TODO - could be relaxed by config
                buildRow(names, rawValues, types, factory)
            }
        }
    }

    private fun buildRow(names: Array<String>, rawValues: Array<Any?>, types: Array<String>, factory: RowFactory?): Row = when (factory) {
        null -> Json.MutableObject().apply {
            putAll(names, rawValues, types)
        }
        else -> factory.new(kindOf(names, rawValues)).also { result ->
            when (result) {
                is Instance -> {
                    result.putNamesValues(names, rawValues, types)
                    result.setClean()
                }
                is Json.MutableObject -> result.putAll(names, rawValues, types)
            }
        }
    }

    override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Flow<Row> {
        val (schema, query) = getSingleQuery(path, mutable, params.keys)
        val values = query.params.map {
            params[it]
                ?:
                if (params.containsKey(it)) null
                else throw SkormException("Missing parameter: $it")
        }.toTypedArray()
        val connector = connectorFor(mutable)
        return flow {
            val result = blocking { connector.query(schema, query.stmt, *values) }
            try {
                val (names, it, types) = result
                while (true) {
                    // one hop to the blocking context per page, the rows handed over here
                    val page = blocking { buildList { while (size < ROW_PAGE && it.hasNext()) add(buildRow(names, it.next(), types, factory)) } }
                    if (page.isEmpty()) break
                    page.forEach { emit(it) }
                }
            } finally {
                // exhausted, abandoned or cancelled: what backs the rows is released either way
                withContext(NonCancellable + blockingContext) { result.close() }
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
                        totalChanged += blocking { connector.mutate(schema, query.stmt, *query.params.map { params[it] }.toTypedArray()) }
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
            val values = query.params.map { params[it] }.toTypedArray()
            return blocking { connector.mutate(schema, query.stmt, *values) }
        }
    }

    override suspend fun begin(schema: String): Transaction {
        return CoreProcessorTransaction(blocking { connector.begin(schema) }, this)
    }

    private fun getSingleQuery(path: String, mutable: Boolean, params: Collection<String>) = queries.getOrElse(Pair(path, mutable)) {
        throw SkormException("attribute not found: $path")
    }.let {
        Pair(it.schema,
            it.queries(params).firstOrNull() ?: throw SkormException("single query expected: $path"))
    }

    private fun getMutationQueries(path: String, params: Collection<String>) = queries.getOrElse(Pair(path, true)) {
        throw SkormException("attribute not found: $path")
    }.let {
        Pair(it.schema, it.queries(params))
    }

    private fun Json.MutableObject.putAll(names: Array<String>, values: Array<Any?>, types: Array<String>) {
        for (i in names.indices) {
            val filtered = if (i < types.size) downstreamFilter(types[i], values[i]) else values[i]
            put(readMapper(names[i]), filtered)
        }
    }

    private fun Instance.putNamesValues(names: Array<String>, values: Array<Any?>, types: Array<String>) {
        // Apply filters based on column types from metadata
        val filteredValues = values.mapIndexed { i, value ->
            if (i < types.size) downstreamFilter(types[i], value) else value
        }.toTypedArray()
        putRawFields(names.zip(filteredValues).toMap())
    }

    override fun downstreamMapping(name: String) = readMapper(name)

    override fun upstreamMapping(name: String) = writeMapper(name)

    override fun downstreamFilter(type: String, value: Any?) =
        readFilters[type]?.let { filter -> filter(value) } ?: value

    // the row's discriminator, when the rows carry one
    private fun kindOf(names: Array<String>, values: Array<Any?>): String? =
        names.indexOf(KIND_COLUMN).takeIf { it >= 0 }?.let { values[it] as? String }

    // sql utils
    /** an entity's own columns from its table; everything from a wider [Entity.source], its joins included */
    private fun Entity.selection() = source?.let { "* FROM $it" }
        ?: "${fields.values.joinToString(", ") { writeMapper(it.name) }} FROM ${schema.name}.${writeMapper(name)}"

    private fun Entity.generateBrowseStatement(): QueryDefinition {
        val stmt = "SELECT ${selection()};"
        return QueryDefinition(stmt, emptyList())
    }

    private fun Entity.generateFetchStatement(): QueryDefinition {
        val stmt = "SELECT ${selection()} WHERE ${
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
        return QueryDefinition(stmt, params.toList() + primaryKey.map { it.name })
    }

    // Map pseudo-types to actual SQL types for parameter casting
    private fun Field.castType(): String = when (type) {
        "serial" -> "integer"
        "bigserial" -> "bigint"
        "smallserial" -> "smallint"
        else -> type
    }

    private fun Field.parameter(): String {
        return connector.getMetaInfos().let { meta ->
            when {
                meta.strictColumnTypes && meta.columnMarkers -> "?::${this.castType()}"
                meta.strictColumnTypes -> "CAST(? AS ${this.castType()})"
                else -> "?"
            }
        }
    }

    companion object {
        /** rows built per hop to the blocking context */
        const val ROW_PAGE = 1000
    }

    override fun close() {
        connector.close()
        if (readConnector !== connector) readConnector.close()
    }
}

/** Everything runs on the transaction's connection, reads of the read-only database included. */
class CoreProcessorTransaction(txConnector: TransactionConnector, parent: CoreProcessor) : CoreProcessor(txConnector, txConnector, parent.blockingContext), Transaction {
    init {
        // share the parent's registry, mappers and filters: only the connector differs
        queries = parent.queries
        readMapper = parent.readMapper
        writeMapper = parent.writeMapper
        readFilters = parent.readFilters
        writeFilters = parent.writeFilters
    }

    private val txConnector: TransactionConnector get() = connector as TransactionConnector

    override suspend fun rollback() {
        txConnector.rollback()
    }

    override suspend fun commit() {
        txConnector.commit()
    }
}
