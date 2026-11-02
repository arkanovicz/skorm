package com.republicate.skorm.jdbc

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.datetime.LocalDateTime
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A `timestamp with time zone` reads as a `kotlin.time.Instant` and an Instant binds to it, whatever the JVM's zone:
 * the literal carries +05, and PostgreSQL's driver would otherwise hand back a java.sql.Timestamp shifted to the JVM's zone.
 * A plain `timestamp` stays a LocalDateTime.
 */
class TimestampTzTest {

    private val literal = Instant.parse("2026-11-02T07:34:56Z")
    private val bound = Instant.parse("2026-11-02T07:34:56.123456Z")

    private fun JdbcConnector.roundTrip(tzType: String) {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris")) // never UTC, or a shift would go unseen
        initialize()
        try {
            mutate(null, "CREATE TABLE stamps (id INT PRIMARY KEY, tz $tzType, plain TIMESTAMP)")
            mutate(null, "INSERT INTO stamps VALUES (1, TIMESTAMP WITH TIME ZONE '2026-11-02 12:34:56+05:00', TIMESTAMP '2026-11-02 12:34:56')")
            query(null, "SELECT tz, plain FROM stamps WHERE id = 1").use {
                val row = it.values.next()
                assertEquals(literal, row[0])
                assertEquals(LocalDateTime(2026, 11, 2, 12, 34, 56), row[1])
            }
            mutate(null, "INSERT INTO stamps (id, tz) VALUES (2, ?)", bound)
            query(null, "SELECT tz FROM stamps WHERE id = ?", 2).use { assertEquals(bound, it.values.next()[0]) }
            query(null, "SELECT id FROM stamps WHERE tz = ?", literal).use { assertEquals(1, it.values.next()[0]) }
        } finally {
            close()
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun h2() = JdbcConnector("jdbc:h2:mem:timestamptz;DB_CLOSE_DELAY=-1").roundTrip("TIMESTAMP WITH TIME ZONE")

    @Test
    fun postgresql() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            JdbcConnector(pg.jdbcUrl, pg.username, pg.password).roundTrip("timestamptz")
        }
    }
}
