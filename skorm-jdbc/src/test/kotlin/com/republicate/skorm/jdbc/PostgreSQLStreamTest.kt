package com.republicate.skorm.jdbc

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * On PostgreSQL a stream fetches `fetchSize` rows at a time: the first row of a 2M-row query arrives, and the
 * result closes, in a fraction of the time the buffered read of the same query takes; closed early, the read's
 * transaction has ended and its connection is back in the pool.
 *
 * Skipped when there is no Docker on the host or SKORM_SKIP_PG_TESTS is set; a Docker that is present but
 * unreachable fails the test.
 */
class PostgreSQLStreamTest {

    private fun requireDocker() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
    }

    @Test
    fun aStreamFetchesAPageAtATime() {
        requireDocker()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            val connector = JdbcConnector(pg.jdbcUrl, pg.username, pg.password)
            connector.configure(mapOf("fetchSize" to 100))
            connector.initialize()
            try {
                // in the select list the series is produced row by row; in FROM, a function scan would materialize it first
                val sql = "SELECT pg_backend_pid(), generate_series(1, 2000000)"
                // connections opened beforehand, out of the timings: one per pool
                connector.query(null, "SELECT 1").use { it.values.next() }
                val idle = connector.stream(null, "SELECT pg_backend_pid()").use { it.values.next()[0] }

                // the autocommit path: the driver buffers the whole result before the first row
                val (count, buffered) = measureTimedValue {
                    connector.query(null, sql).use { result ->
                        var n = 0
                        while (result.values.hasNext()) { result.values.next(); ++n }
                        n
                    }
                }
                assertEquals(2_000_000, count)

                val (pid, streamed) = measureTimedValue {
                    connector.stream(null, sql).use { it.values.next()[0] }
                }
                println("PostgreSQLStreamTest: buffered read of 2M rows $buffered, streamed first row + close $streamed")
                assertTrue(streamed * 5 <= buffered, "streamed first row + close took $streamed, the buffered read $buffered")
                assertEquals(idle, pid, "the stream ran on the pooled connection")

                val state = connector.query(null, "SELECT state FROM pg_stat_activity WHERE pid = ?", pid).use { it.values.next()[0] }
                assertEquals("idle", state, "closed early, the read's transaction has ended")
                assertEquals(idle, connector.stream(null, "SELECT pg_backend_pid()").use { it.values.next()[0] }, "and its connection is back in the pool")
            } finally {
                connector.close()
            }
        }
    }
}
