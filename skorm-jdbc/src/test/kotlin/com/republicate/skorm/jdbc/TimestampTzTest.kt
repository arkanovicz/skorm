package com.republicate.skorm.jdbc

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A `timestamp with time zone` reads as a `kotlin.time.Instant` and an Instant binds to it, whatever the JVM's zone:
 * the literal carries +05, and PostgreSQL's driver would otherwise hand back a java.sql.Timestamp shifted to the JVM's zone.
 * A plain `timestamp` or `time` is a wall clock, so the JVM's zone must not touch it either: 02:30 on the night Paris
 * springs forward exists in the column, not on the JVM's clock, and a java.sql.Timestamp would move it to 03:30.
 */
class TimestampTzTest {

    private val literal = Instant.parse("2026-11-02T07:34:56Z")
    private val bound = Instant.parse("2026-11-02T07:34:56.123456Z")
    private val gap = LocalDateTime(2026, 3, 29, 2, 30)
    private val gapTime = LocalTime(2, 30)

    private fun JdbcConnector.pinned(tzType: String, block: JdbcConnector.() -> Unit) {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris")) // never UTC, or a shift would go unseen
        initialize()
        try {
            mutate(null, "CREATE TABLE stamps (id INT PRIMARY KEY, tz $tzType, plain TIMESTAMP, t TIME)")
            block()
        } finally {
            close()
            TimeZone.setDefault(zone)
        }
    }

    private fun JdbcConnector.zoned() {
        mutate(null, "INSERT INTO stamps (id, tz, plain) VALUES (1, TIMESTAMP WITH TIME ZONE '2026-11-02 12:34:56+05:00', TIMESTAMP '2026-11-02 12:34:56')")
        query(null, "SELECT tz, plain FROM stamps WHERE id = 1").use {
            val row = it.values.next()
            assertEquals(literal, row[0])
            assertEquals(LocalDateTime(2026, 11, 2, 12, 34, 56), row[1])
        }
        mutate(null, "INSERT INTO stamps (id, tz) VALUES (2, ?)", bound)
        query(null, "SELECT tz FROM stamps WHERE id = ?", 2).use { assertEquals(bound, it.values.next()[0]) }
        query(null, "SELECT id FROM stamps WHERE tz = ?", literal).use { assertEquals(1, it.values.next()[0]) }
    }

    private fun JdbcConnector.wallClockRead() {
        mutate(null, "INSERT INTO stamps (id, plain, t) VALUES (3, TIMESTAMP '2026-03-29 02:30:00', TIME '02:30:00')")
        query(null, "SELECT plain, t FROM stamps WHERE id = 3").use {
            val row = it.values.next()
            assertEquals(gap, row[0])
            assertEquals(gapTime, row[1])
        }
    }

    private fun JdbcConnector.wallClockWrite() {
        mutate(null, "INSERT INTO stamps (id, plain, t) VALUES (4, ?, ?)", gap, gapTime)
        query(null, "SELECT CAST(plain AS VARCHAR), CAST(t AS VARCHAR) FROM stamps WHERE id = 4").use {
            val row = it.values.next()
            assertEquals("2026-03-29 02:30:00", row[0].toString())
            assertEquals("02:30:00", row[1].toString())
        }
        query(null, "SELECT id FROM stamps WHERE plain = ?", gap).use { assertEquals(4, it.values.next()[0]) }
    }

    private fun h2(name: String) = JdbcConnector("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")

    @Test fun h2Zoned() = h2("zoned").pinned("TIMESTAMP WITH TIME ZONE") { zoned() }
    @Test fun h2WallClockRead() = h2("wcr").pinned("TIMESTAMP WITH TIME ZONE") { wallClockRead() }
    @Test fun h2WallClockWrite() = h2("wcw").pinned("TIMESTAMP WITH TIME ZONE") { wallClockWrite() }

    private fun postgresql(block: JdbcConnector.() -> Unit) {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            JdbcConnector(pg.jdbcUrl, pg.username, pg.password).pinned("timestamptz", block)
        }
    }

    @Test fun postgresqlZoned() = postgresql { zoned() }
    @Test fun postgresqlWallClockRead() = postgresql { wallClockRead() }
    @Test fun postgresqlWallClockWrite() = postgresql { wallClockWrite() }
}
