package com.republicate.skorm

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual fun <T> blockingOn(context: CoroutineContext, block: suspend () -> T): T =
    throw UnsupportedOperationException("blocking calls are not available on this platform: use the suspend function")

internal actual fun ambientCompanion(ambient: AmbientTransaction): CoroutineContext = EmptyCoroutineContext

internal actual fun ambientOnThread(): AmbientTransaction? = null
