package com.republicate.skorm.jdbc

import com.republicate.skorm.SkormException
import com.republicate.skorm.core.CoreProcessor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A creation script runs when the database has none of its kddl schema's schemas, whatever else it holds. */
class CreateMissingSchemasTest {

    private fun script(vararg schemas: String) = schemas.joinToString("\n") {
        "CREATE SCHEMA $it;\nCREATE TABLE $it.item (label varchar(20));\nINSERT INTO $it.item VALUES ('made; once');"
    }

    @Test
    fun runsOncePerKddlSchema() = runTest {
        val connector = JdbcConnector("jdbc:h2:mem:create_missing;DB_CLOSE_DELAY=-1")
        val processor = CoreProcessor(connector)
        processor.initialize()
        fun items(schema: String): Long {
            val result = connector.query(schema, "SELECT count(*) FROM item")
            return (result.values.next()[0] as Number).toLong().also { result.close() }
        }

        // an empty database: created
        assertTrue(processor.createMissingSchemas(listOf("shelf")) { script("shelf") })
        assertEquals(1, items("shelf"))
        // there already: the script is not even read
        assertFalse(processor.createMissingSchemas(listOf("shelf")) { error("read") })
        // an importer's database is not empty, its own schemas are still to create
        assertTrue(processor.createMissingSchemas(listOf("club", "annex")) { script("club", "annex") })
        assertEquals(1, items("annex"))
        assertFalse(processor.createMissingSchemas(listOf("Club", "ANNEX")) { error("read") })
        // half there: neither created over nor taken for done
        val partial = assertFailsWith<SkormException> { processor.createMissingSchemas(listOf("club", "attic")) { error("read") } }
        assertTrue("club already there" in partial.message!! && "attic missing" in partial.message!!, partial.message)
    }
}
