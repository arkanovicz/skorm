package com.republicate.skorm

sealed interface Marker
class StreamMarker<T>(val stream:T)
class GeneratedKeyMarker(val colName: String)

interface Transaction {
    suspend fun savePoint(name: String): Unit
    suspend fun rollback(savePoint: String?): Unit
    suspend fun commit(): Unit
}

interface Processor {
    // attributes
    suspend fun eval(path: String, params: Map<String, Any?>): Any?
    suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity? = null): Instance?
    suspend fun query(path: String, params: Map<String, Any?>, result: Entity? = null): Sequence<Instance>
    suspend fun perform(path: String, params: Map<String, Any?>): Long

    suspend fun attempt(path: String, params: Map<String, Any?>): List<Int>
    // transaction
    suspend fun begin(): Transaction
}
