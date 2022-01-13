package com.republicate.skorm

class QueryResult(
    val names: Array<String>,
    val values: Iterator<Array<Any?>>
) {
    operator fun component1() = names
    operator fun component2() = values
}

interface Connector {
    // queries
    fun query(query: String, vararg params: Any?): QueryResult

    // mutations
    fun mutate(query: String, vararg params: Any?): Long
}
