package com.republicate.skorm.jdbc

import com.republicate.skorm.Database
import com.republicate.skorm.Entity
import com.republicate.skorm.Field
import com.republicate.skorm.Schema
import com.republicate.skorm.core.CoreProcessor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * A flow from `CoreProcessor.query` closes its streamed result however the collection ends, so the JDBC
 * connection it ran on goes back to the pool; and it is cold, so it can be collected again.
 */
class StreamFlowTest {

    private class Db(processor: CoreProcessor) : Database("store", processor)
    private class Sch(db: Database) : Schema("flow", db)
    private class Ent(schema: Schema) : Entity("item", schema)

    private val connector = JdbcConnector("jdbc:h2:mem:stream_flow;DB_CLOSE_DELAY=-1")
    private val item = Ent(Sch(Db(CoreProcessor(connector)))).apply {
        addField(Field("itemId", "int", isPrimary = true))
    }

    init {
        connector.initialize()
        connector.mutate(null, "CREATE SCHEMA IF NOT EXISTS FLOW")
        connector.mutate(null, "CREATE TABLE IF NOT EXISTS FLOW.ITEM (ITEM_ID INT PRIMARY KEY)")
        connector.mutate("flow", "DELETE FROM FLOW.ITEM")
        connector.mutate("flow", "INSERT INTO FLOW.ITEM (ITEM_ID) VALUES (1), (2), (3), (4), (5)")
        item.schema.database.initialize()
    }

    @AfterTest
    fun close() = connector.close()

    /** the session a stream of the item's schema runs on, the result closed: a released connection comes back first */
    private fun streamSession(): Any? = connector.stream("flow", "SELECT SESSION_ID()").use { it.values.next()[0] }

    @Test
    fun anAbandonedFlowReleasesItsConnection() = runTest {
        val idle = streamSession()
        val rows = item.browse()
        rows.take(1).collect {
            assertNotEquals(idle, streamSession(), "while collected, the flow holds the pooled connection")
        }
        assertEquals(idle, streamSession(), "abandoned after one row, the connection is back in the pool")
        rows.first()
        assertEquals(idle, streamSession())
        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it["itemId"] }.toList(), "cold: collected again from the start")
        assertEquals(idle, streamSession())
    }

    @Test
    fun aFailedCollectionReleasesItsConnection() = runTest {
        val idle = streamSession()
        assertFailsWith<IllegalStateException> { item.browse().collect { error("collector failure") } }
        assertEquals(idle, streamSession(), "failed in the collector, the connection is back in the pool")
    }
}
