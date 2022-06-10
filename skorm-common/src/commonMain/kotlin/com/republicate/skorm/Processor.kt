package com.republicate.skorm

import com.republicate.kson.Json

sealed interface Marker
class StreamMarker<T>(val stream:T)
class GeneratedKeyMarker(val colName: String)

interface Transaction : Processor {
    suspend fun rollback(): Unit
    suspend fun commit(): Unit
}

interface Processor: Configurable {

    // attributes
    suspend fun eval(path: String, params: Map<String, Any?>): Any?
    suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity? = null): Json.Object?
    suspend fun query(path: String, params: Map<String, Any?>, result: Entity? = null): Sequence<Json.Object>
    suspend fun perform(path: String, params: Map<String, Any?>): Long

    // transaction
    suspend fun begin(): Transaction
}

suspend fun Processor.transaction(block: Transaction.()->Unit) {
    val tx = begin()
    try {
        block.invoke(tx)
        tx.commit()
    } catch (t: Throwable) {
        tx.rollback()
        throw t
    }
}
