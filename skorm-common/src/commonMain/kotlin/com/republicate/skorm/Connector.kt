package com.republicate.skorm

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/** A result set: [values] fetch rows on demand, [close] releases what backs them once the reader is done, exhausted or not. */
class QueryResult @JvmOverloads constructor(
    val names: Array<String>,
    val values: Iterator<Array<Any?>>,
    val types: Array<String> = emptyArray(),
    private val closer: () -> Unit = {}
) : AutoCloseable {
    override fun close() = closer()
    operator fun component1() = names
    operator fun component2() = values
    operator fun component3() = types
}

interface MetaInfos {
    val identifierQuoteChar: Char
    val identifierInternalCase: Char // 'U'ppercase, 'L'owercase, 'S'ensitive
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("isStrictColumnTypes")
    val strictColumnTypes: Boolean
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("hasColumnMarkers")
    val columnMarkers: Boolean
}

interface Connector: Configurable, AutoCloseable {
    // meta
    @Throws(SkormException::class)
    fun getMetaInfos(): MetaInfos

    // queries
    @Throws(SkormException::class)
    fun query(schema: String, query: String, vararg params: Any?): QueryResult

    /** A query expected to return many rows: fetched as the reader goes when the connector can, and closed by the reader. */
    @Throws(SkormException::class)
    fun stream(schema: String, query: String, vararg params: Any?): QueryResult = query(schema, query, *params)

    // mutations
    @Throws(SkormException::class)
    fun mutate(schema: String, query: String, vararg params: Any?): Long

    // transactions
    @Throws(SkormException::class)
    fun begin(schema: String): TransactionConnector
}

open class ScopedConnector(val connector: Connector, val schema: String) {

    // queries
    @Throws(SkormException::class)
    fun query(query: String, vararg params: Any?) = connector.query(schema, query, *params)

    // mutations
    @Throws(SkormException::class)
    fun mutate(query: String, vararg params: Any?) = connector.mutate(schema, query, *params)

    // transactions
    @Throws(SkormException::class)
    fun begin() = connector.begin(schema)
}

interface TransactionConnector: Connector {
    @Throws(SkormException::class)
    fun commit()

    @Throws(SkormException::class)
    fun rollback()
}
