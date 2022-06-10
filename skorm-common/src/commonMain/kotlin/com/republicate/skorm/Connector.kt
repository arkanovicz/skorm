package com.republicate.skorm

class QueryResult(
    val names: Array<String>,
    val values: Iterator<Array<Any?>>
) {
    operator fun component1() = names
    operator fun component2() = values
}

interface Connector: Configurable {
    // queries
    @Throws(SkormException::class)
    fun query(query: String, vararg params: Any?): QueryResult

    // mutations
    @Throws(SkormException::class)
    fun mutate(query: String, vararg params: Any?): Long

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
