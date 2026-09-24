package com.republicate.skorm.jdbc

import com.republicate.skorm.SkormException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `JdbcConnector.stream` on H2: rows are read as the reader goes, and closing the result commits and releases
 * the autocommit-off connection it ran on. Which connection is observed through `SESSION_ID()`: the pool hands
 * out its first free connection, so a released one comes back, a held one doesn't.
 */
class StreamTest {

    companion object {
        private val ticks = AtomicInteger()

        /** H2 alias: counts the rows the engine has computed */
        @JvmStatic
        fun tick(x: Long): Long {
            ticks.incrementAndGet()
            return x
        }
    }

    // lazy execution: H2 embedded computes rows on next() instead of materializing the result
    private val connector = JdbcConnector("jdbc:h2:mem:stream;DB_CLOSE_DELAY=-1;LAZY_QUERY_EXECUTION=TRUE;LOCK_TIMEOUT=300")

    init {
        connector.initialize()
        connector.mutate(null, "CREATE ALIAS IF NOT EXISTS TICK DETERMINISTIC FOR '${StreamTest::class.java.name}.tick'")
        connector.mutate(null, "CREATE TABLE IF NOT EXISTS ITEM (ID INT PRIMARY KEY, V INT)")
        connector.mutate(null, "DELETE FROM ITEM")
        connector.mutate(null, "INSERT INTO ITEM (ID, V) VALUES (1, 0), (2, 0), (3, 0)")
    }

    @AfterTest
    fun close() = connector.close()

    /** the session a stream runs on, the result closed */
    private fun streamSession(): Any? = connector.stream(null, "SELECT SESSION_ID()").use { it.values.next()[0] }

    @Test
    fun rowsArriveLazilyAndCloseReleasesTheConnection() {
        val idle = streamSession()
        ticks.set(0)
        val result = connector.stream(null, "SELECT TICK(X), SESSION_ID() FROM SYSTEM_RANGE(1, 100000)")
        val first = result.values.next()
        assertEquals(1L, first[0])
        assertTrue(ticks.get() < 100, "one row read, ${ticks.get()} computed")
        assertEquals(idle, first[1], "the stream ran on the free pooled connection")
        result.close()
        assertEquals(idle, streamSession(), "closed early, the connection is back in the pool")

        connector.mutate(null, "UPDATE ITEM SET V = 5 WHERE ID = 2")
        assertEquals(5, connector.query(null, "SELECT V FROM ITEM WHERE ID = 2").use { it.values.next()[0] })
    }

    @Test
    fun anOpenStreamHoldsItsConnection() {
        val idle = streamSession()
        connector.stream(null, "SELECT ID FROM ITEM").use { result ->
            result.values.next()
            assertNotEquals(idle, streamSession(), "while a stream is open, the next one gets another connection")
        }
        assertEquals(idle, streamSession())
    }

    @Test
    fun closeCommitsTheRead() {
        val result = connector.stream(null, "SELECT ID FROM ITEM WHERE ID = 1 FOR UPDATE")
        result.values.next()
        // the read's transaction is open while the result is: its row lock is observable
        assertFailsWith<SkormException> { connector.mutate(null, "UPDATE ITEM SET V = 1 WHERE ID = 1") }
        result.close()
        assertEquals(1L, connector.mutate(null, "UPDATE ITEM SET V = 2 WHERE ID = 1"), "closed, the read's transaction has ended")
    }

    @Test
    fun insideATransactionCloseLeavesTheTransactionOpen() {
        val tx = connector.begin("public")
        tx.mutate("public", "UPDATE ITEM SET V = 9 WHERE ID = 3")
        tx.stream("public", "SELECT V FROM ITEM WHERE ID = 3").use { assertEquals(9, it.values.next()[0]) }
        tx.stream("public", "SELECT V FROM ITEM WHERE ID = 3").use { assertEquals(9, it.values.next()[0], "still usable after a stream closed") }
        tx.rollback()
        assertEquals(0, connector.query(null, "SELECT V FROM ITEM WHERE ID = 3").use { it.values.next()[0] }, "the stream's close did not commit")
    }
}
