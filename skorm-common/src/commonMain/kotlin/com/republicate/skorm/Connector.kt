package com.republicate.skorm

sealed interface Marker
class StreamMarker<T>(val stream:T)
class GeneratedKeyMarker(val colName: String)

interface Row {

}

interface Connector {

    // queries
    fun query(query: String, vararg params: Any?): Sequence<Row>

    // mutations
    fun mutate(query: String, vararg params: Any?): Long

    // transaction
    fun begin()
    fun savePoint(name: String)
    fun rollback(savePoint: String?)
    fun commit()
}
