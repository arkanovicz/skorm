package com.republicate.skorm

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual fun <T> blockingOn(context: CoroutineContext, block: suspend () -> T): T = runBlocking(context) { block() }

// no thread-local context elements on native: a blocking call joins no enclosing transaction
internal actual fun ambientCompanion(ambient: AmbientTransaction): CoroutineContext = EmptyCoroutineContext

internal actual fun ambientOnThread(): AmbientTransaction? = null
