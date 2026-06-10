package com.republicate.skorm.jdbc

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characterizes how a String parameter binds to a native PostgreSQL enum column, with the
 * `CREATE CAST (varchar AS enum) WITH INOUT AS IMPLICIT` that kddl emits alongside every enum.
 *
 * Findings (verified against a real Postgres):
 *  - assignment context (INSERT/SET): a `setString` value binds fine — the implicit cast applies;
 *  - expression context (WHERE enum = ?): a `setString` value FAILS
 *    (`operator does not exist: enum = character varying`) — PG won't apply the implicit cast in
 *    operator resolution;
 *  - binding the same value untyped (`setObject(i, v, Types.OTHER)`) works in BOTH contexts —
 *    PG infers the type from context. That is the portable fix (no per-column type knowledge).
 *
 * Skipped when Docker is unavailable or SKORM_SKIP_PG_TESTS is set (container start is slow).
 */
class PostgreSQLEnumBindingTest {

    private fun shouldRun() =
        System.getenv("SKORM_SKIP_PG_TESTS") == null &&
            runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

    @Test
    fun untypedBindingIsRequiredForEnumComparisons() {
        assumeTrue(shouldRun(), "Docker unavailable or SKORM_SKIP_PG_TESTS set")
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
                conn.createStatement().use { st ->
                    st.execute("CREATE TYPE genre AS ENUM ('fiction', 'poetry')")
                    st.execute("CREATE CAST (varchar AS genre) WITH INOUT AS IMPLICIT")
                    st.execute("CREATE TABLE book (id serial PRIMARY KEY, title varchar(100), genre genre)")
                }

                // assignment context: setString into an enum column — works via the implicit cast
                conn.prepareStatement("INSERT INTO book (title, genre) VALUES (?, ?)").use { ps ->
                    ps.setString(1, "Dune")
                    ps.setString(2, "fiction")
                    ps.executeUpdate()
                }

                // expression context with setString: FAILS — PG won't cast varchar -> enum for `=`
                val setStringFailed = try {
                    conn.prepareStatement("UPDATE book SET title = ? WHERE genre = ?").use { ps ->
                        ps.setString(1, "Dune (a)")
                        ps.setString(2, "fiction")
                        ps.executeUpdate()
                    }
                    false
                } catch (e: SQLException) {
                    true
                }
                assertTrue(setStringFailed, "WHERE enum = setString(varchar) should fail on Postgres")

                // expression context with untyped binding: WORKS — PG infers the enum from context
                val updated = conn.prepareStatement("UPDATE book SET title = ? WHERE genre = ?").use { ps ->
                    ps.setString(1, "Dune (b)")
                    ps.setObject(2, "fiction", Types.OTHER)
                    ps.executeUpdate()
                }
                assertEquals(1, updated, "WHERE enum = setObject(OTHER) should match")

                // untyped binding must also be safe for a plain varchar column (the fix binds all Strings untyped)
                val varcharUpdated = conn.prepareStatement("UPDATE book SET title = ? WHERE title = ?").use { ps ->
                    ps.setObject(1, "Dune (c)", Types.OTHER)
                    ps.setObject(2, "Dune (b)", Types.OTHER)
                    ps.executeUpdate()
                }
                assertEquals(1, varcharUpdated, "untyped binding should also work for varchar columns")
            }

            // the fix: the skorm connector (pedanticCasts vendor flag) binds strings untyped on
            // Postgres, so an enum comparison works through the real stack.
            val connector = JdbcConnector(pg.jdbcUrl, pg.username, pg.password)
            connector.initialize()
            val matched = connector.mutate(null, "UPDATE book SET title = ? WHERE genre = ?", "via skorm", "fiction")
            assertEquals(1L, matched, "skorm connector should bind the enum comparison correctly on Postgres")
            connector.close()
        }
    }
}
