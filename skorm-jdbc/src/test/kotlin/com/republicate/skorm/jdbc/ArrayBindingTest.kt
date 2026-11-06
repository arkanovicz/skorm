package com.republicate.skorm.jdbc

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals

/**
 * A Kotlin List binds as a SQL array, the shape `WHERE col = ANY(?)` wants. H2 gets a `createArrayOf` array,
 * PostgreSQL an array literal bound untyped, which the column types: an enum column and an empty list need no cast.
 */
class ArrayBindingTest {

    enum class Level { low, high }

    private fun JdbcConnector.ids(sql: String, vararg params: Any?): List<Int> =
        query(null, sql, *params).use { result -> generateSequence { if (result.values.hasNext()) result.values.next()[0] as Int else null }.toList() }

    private fun JdbcConnector.roundTrip(pg: Boolean) {
        initialize()
        try {
            mutate(null, "CREATE TABLE t (id INT PRIMARY KEY, name VARCHAR(20), d DATE)")
            mutate(null, "INSERT INTO t VALUES (1, 'a,b', DATE '2026-01-01'), (2, 'he said \"hi\"', DATE '2026-01-02'), (3, 'back\\slash', DATE '2026-01-03')")
            assertEquals(listOf(1, 3), ids("SELECT id FROM t WHERE id = ANY(?) ORDER BY id", listOf(1, 3)))
            assertEquals(listOf(1, 3), ids("SELECT id FROM t WHERE id = ANY(?) ORDER BY id", intArrayOf(1, 3)))
            assertEquals(emptyList(), ids("SELECT id FROM t WHERE id = ANY(?)", emptyList<Int>()))
            // elements that a literal has to quote and escape
            assertEquals(listOf(1, 2, 3), ids("SELECT id FROM t WHERE name = ANY(?) ORDER BY id", listOf("a,b", "he said \"hi\"", "back\\slash")))
            // elements converted like parameters: a kotlinx date
            assertEquals(listOf(2), ids("SELECT id FROM t WHERE d = ANY(?)", listOf(LocalDate(2026, 1, 2))))
            if (pg) {
                mutate(null, "CREATE TYPE level AS ENUM ('low', 'high')")
                mutate(null, "CREATE TABLE e (id INT PRIMARY KEY, lvl level)")
                mutate(null, "INSERT INTO e VALUES (1, 'low'), (2, 'high')")
                // a native enum column: the untyped literal is the only binding PostgreSQL's polymorphic operators accept
                assertEquals(listOf(2), ids("SELECT id FROM e WHERE lvl = ANY(?)", listOf(Level.high)))
            }
        } finally {
            close()
        }
    }

    @Test fun h2() = JdbcConnector("jdbc:h2:mem:arrays;DB_CLOSE_DELAY=-1").roundTrip(pg = false)

    @Test fun postgresql() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            JdbcConnector(pg.jdbcUrl, pg.username, pg.password).roundTrip(pg = true)
        }
    }
}
