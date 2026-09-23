package com.republicate.skorm

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

actual fun <T> blockingOn(context: CoroutineContext, block: suspend () -> T): T = runBlocking(context) { block() }

private val ambientHere = ThreadLocal<AmbientTransaction?>()

/** Installs the transaction as a thread-local wherever its coroutine runs, restoring the previous one after. */
private class AmbientOnThread(private val ambient: AmbientTransaction) : ThreadContextElement<AmbientTransaction?> {
    companion object Key : CoroutineContext.Key<AmbientOnThread>
    override val key get() = Key
    override fun updateThreadContext(context: CoroutineContext): AmbientTransaction? =
        ambientHere.get().also { ambientHere.set(ambient) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: AmbientTransaction?) = ambientHere.set(oldState)
}

internal actual fun ambientCompanion(ambient: AmbientTransaction): CoroutineContext = AmbientOnThread(ambient)

internal actual fun ambientOnThread(): AmbientTransaction? = ambientHere.get()
