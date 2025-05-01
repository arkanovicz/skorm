package com.republicate.skorm

class QueryResult(
    val names: Array<String>,
    val values: Iterator<Array<Any?>>
) {
    operator fun component1() = names
    operator fun component2() = values
}

interface MetaInfos {
    val identifierQuoteChar: Char
    val identifierInternalCase: Char // 'U'ppercase, 'L'owercase, 'S'ensitive
}

interface Connector: Configurable, AutoCloseable {
    // meta
    @Throws(SkormException::class)
    fun getMetaInfos(): MetaInfos

    // queries
    @Throws(SkormException::class)
    fun query(schema: String, query: String, vararg params: Any?): QueryResult

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
