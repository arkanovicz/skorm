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

interface Connector: Configurable {
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
    fun begin(): TransactionConnector
}

interface TransactionConnector: Connector {
    @Throws(SkormException::class)
    fun commit()

    @Throws(SkormException::class)
    fun rollback()

}
