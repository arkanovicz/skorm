package com.republicate.skorm

import com.republicate.kson.Json
import kotlinx.coroutines.flow.Flow
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

/** What a row is read into: an [Instance] or a plain kson object. */
typealias Row = Map<String, Any?>

/** Builds the object a row is read into. */
fun interface RowFactory {
    fun new(): Row
    /** [kind] is the row's discriminator when the rows carry one: a hierarchy root builds the subclass it names. */
    fun new(kind: String?): Row = new()
}

interface Transaction : Processor {
    suspend fun rollback(): Unit
    suspend fun commit(): Unit
    override val readOnly: Transaction get() = ReadOnlyTransaction(this)
}

interface Processor: Configurable, AutoCloseable {
    // configuration
    fun register(entity: Entity) {}

    // TODO - review
    // fun register(path: String, definition: QueryDef ) {}

    // attributes; [mutable] says the caller is a holder of the mutable database, whose reads run where its writes do
    suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean = false): Any?
    suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory? = null, mutable: Boolean = false): Row?
    /** The rows, fetched as the flow is collected: a cold flow, collected once, inside the transaction it was queried in. */
    suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory? = null, mutable: Boolean = false): Flow<Row>
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

    /** This processor as a read-only database holds it: the same reads, no writes. */
    val readOnly: Processor get() = ReadOnlyProcessor(this)
}

/**
 * The processor of a read-only database: reads delegate, writes throw. The Kotlin types already keep a
 * read-only database from writing; this keeps a caller that ignores them — reflection, a template — from
 * reaching the writes of the processor it shares with the mutable database.
 */
open class ReadOnlyProcessor(internal val delegate: Processor) : Processor by delegate {
    override val readOnly: Processor get() = this
    override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? =
        if (mutable) refuse(path) else delegate.eval(path, params, false)
    override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Row? =
        if (mutable) refuse(path) else delegate.retrieve(path, params, factory, false)
    override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Flow<Row> =
        if (mutable) refuse(path) else delegate.query(path, params, factory, false)
    override suspend fun perform(path: String, params: Map<String, Any?>): Long = refuse(path)
    override suspend fun begin(schema: String): Transaction = delegate.begin(schema).readOnly
    private fun refuse(path: String): Nothing = throw SkormException("read-only processor: cannot write $path")
}

class ReadOnlyTransaction(private val tx: Transaction) : ReadOnlyProcessor(tx), Transaction {
    override val readOnly: Transaction get() = this
    override suspend fun rollback() = tx.rollback()
    override suspend fun commit() = tx.commit()
}

/** The processor behind a read-only view — an extension, so that reflection on the view does not reach it. */
val Processor.underlying: Processor get() = if (this is ReadOnlyProcessor) delegate else this

suspend fun Processor.transaction(schema: String, block: suspend Transaction.() -> Unit) {
    val tx = begin(schema)
    try {
        block.invoke(tx)
        tx.commit()
    } catch (t: Throwable) {
        tx.rollback()
        throw t
    }
}
