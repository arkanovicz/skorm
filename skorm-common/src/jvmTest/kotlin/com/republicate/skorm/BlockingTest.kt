package com.republicate.skorm

import com.republicate.kson.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The bridge blocking twins run through, and the companion that lets them join an enclosing transaction. */
class BlockingTest {

    private val noTransaction = object : Transaction {
        override suspend fun rollback() {}
        override suspend fun commit() {}
        override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? = null
        override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Row? = null
        override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Sequence<Row> = emptySequence()
        override suspend fun perform(path: String, params: Map<String, Any?>): Long = 0
        override suspend fun begin(schema: String): Transaction = this
        override val restMode = false
        override val config = Configuration()
        override fun close() {}
    }
    private class TestDatabase(processor: Processor) : Database("d", processor)

    @Test
    fun blockingRunsTheBlockToCompletion() {
        assertEquals(42, blockingOn(EmptyCoroutineContext) { 42 })
        assertEquals("io", blockingOn(Dispatchers.IO) { "io" })
    }

    @Test
    fun theCompanionExposesTheTransactionToBlockingCallsAndOnlyWhileItRuns() = runBlocking(Dispatchers.Default) {
        val db = TestDatabase(noTransaction)
        val ambient = AmbientTransaction(db, noTransaction)
        assertNull(ambientOnThread())
        withContext(ambient + ambientCompanion(ambient)) {
            assertSame(ambient, ambientOnThread())
            // a blocking call made here sees the transaction in its own coroutine context
            assertSame(ambient, db.blocking { kotlin.coroutines.coroutineContext[AmbientTransaction] })
        }
        assertNull(ambientOnThread())
        // without the companion, a blocking call joins nothing
        withContext(ambient) { assertNull(db.blocking { kotlin.coroutines.coroutineContext[AmbientTransaction] }) }
    }

    @Test
    fun aTwinCalledFromAnotherTwinJoinsTheTransactionOnAnotherThread() = runBlocking(Dispatchers.Default) {
        val db = TestDatabase(noTransaction)
        db.blockingContext = Dispatchers.IO
        db.transaction("s") {
            val ambient = kotlin.coroutines.coroutineContext[AmbientTransaction]
            // the outer twin runs on an IO thread: the inner one finds the transaction only if the companion came along
            val seen = db.blocking { db.blocking { kotlin.coroutines.coroutineContext[AmbientTransaction] } }
            assertSame(ambient, seen)
        }
    }
}
