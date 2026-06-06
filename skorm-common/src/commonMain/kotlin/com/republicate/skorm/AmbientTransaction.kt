package com.republicate.skorm

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/**
 * Coroutine context element carrying the active transaction of a database.
 * Transactions on distinct databases can nest: elements chain through [outer].
 */
class AmbientTransaction(
    val database: Database,
    val tx: Transaction,
    private val outer: AmbientTransaction? = null
): AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AmbientTransaction>

    internal fun find(database: Database): Transaction? =
        generateSequence(this) { it.outer }.firstOrNull { it.database === database }?.tx
}

/**
 * Resolve the processor for a skorm operation: the ambient transaction of this
 * holder's database when one is active in the calling coroutine, the database's
 * own processor otherwise.
 */
suspend fun AttributeHolder.currentProcessor(): Processor =
    database?.let { db -> coroutineContext[AmbientTransaction]?.find(db) } ?: processor

/**
 * Run [block] in a transaction: all skorm operations against this database
 * performed from the block (and its child coroutines) share one transaction,
 * committed on normal exit, rolled back when the block throws (rethrown).
 *
 * A nested call for the same database joins the enclosing transaction (no
 * savepoint): only the outermost block commits or rolls back.
 *
 * Caveats:
 * - a transaction holds a single connection, which is not safe for *parallel*
 *   use: sequential awaits are fine, concurrent fan-out inside the block is not;
 * - lazy [kotlin.sequences.Sequence] results must be iterated inside the block;
 * - [schema] selects the transaction connection; operations on the database's
 *   other schemas still join the transaction.
 */
suspend fun <T> Database.transaction(schema: String, block: suspend () -> T): T {
    val ambient = coroutineContext[AmbientTransaction]
    if (ambient?.find(this) != null) return block() // join the enclosing transaction
    val tx = processor.begin(schema)
    try {
        val result = withContext(AmbientTransaction(this, tx, ambient)) { block() }
        tx.commit()
        return result
    } catch (t: Throwable) {
        tx.rollback()
        throw t
    }
}

/** Single-schema convenience overload of [transaction]. */
suspend fun <T> Database.transaction(block: suspend () -> T): T =
    transaction(schemas.singleOrNull()?.name
        ?: throw SkormException("database $name has ${schemas.size} schemas, please specify one"), block)
