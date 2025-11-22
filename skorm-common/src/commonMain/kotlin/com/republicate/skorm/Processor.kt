package com.republicate.skorm

import com.republicate.kson.Json
import kotlin.jvm.JvmField

sealed interface Marker
class StreamMarker<T>(val stream:T)
class GeneratedKeyMarker(
    val colName: String
) {
    companion object {
        const val PARAM_KEY = "__generated_key__"
    }

    override fun toString() = "out:generated_key($colName)"
}

typealias RowFactory = () -> Json.MutableObject

interface Transaction : Processor {
    suspend fun rollback(): Unit
    suspend fun commit(): Unit
}

interface Processor: Configurable, AutoCloseable {
    // configuration
    fun register(entity: Entity) {}

    // TODO - review
    // fun register(path: String, definition: QueryDef ) {}

    // attributes
    suspend fun eval(path: String, params: Map<String, Any?>): Any?
    suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory? = null): Json.Object?
    suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory? = null): Sequence<Json.Object>
    suspend fun perform(path: String, params: Map<String, Any?>): Long

    // identifiers mapping
    fun downstreamMapping(name: String) = name

    fun upstreamMapping(name: String) = name

    // filtering per value type
    fun downstreamFilter(type: String, value: Any?) = value

    // transaction
    suspend fun begin(schema: String): Transaction

    // in rest mode, instances PK are appended to the path
    val restMode: Boolean
}

suspend fun Processor.transaction(schema: String, block: Transaction.()->Unit) {
    val tx = begin(schema)
    try {
        block.invoke(tx)
        tx.commit()
    } catch (t: Throwable) {
        tx.rollback()
        throw t
    }
}
