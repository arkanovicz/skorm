package com.republicate.skorm

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs [block] to completion before returning, dispatching it on [context]: the calling thread waits.
 * Refused where the platform cannot block a thread (JS, wasm).
 */
expect fun <T> blockingOn(context: CoroutineContext, block: suspend () -> T): T

/** What makes [ambient] visible to blocking calls made from the threads its coroutine runs on. */
internal expect fun ambientCompanion(ambient: AmbientTransaction): CoroutineContext

/** The ambient transaction a coroutine installed on the calling thread, if any. */
internal expect fun ambientOnThread(): AmbientTransaction?

/**
 * Where a blocking twin runs: the database's [Database.blockingContext], joined by the transaction of the
 * coroutine that is rendering, when there is one — a template inside `transaction { }` sees its own writes.
 */
fun <T> Database.blocking(block: suspend () -> T): T =
    blockingOn(blockingContext + (ambientOnThread() ?: EmptyCoroutineContext), block)

fun <T> Instance.blocking(block: suspend () -> T): T = entity.schema.database.blocking(block)

fun <T> Schema.blocking(block: suspend () -> T): T = database.blocking(block)
