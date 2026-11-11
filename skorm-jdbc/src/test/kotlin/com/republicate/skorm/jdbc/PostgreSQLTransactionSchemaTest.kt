package com.republicate.skorm.jdbc

import com.republicate.skorm.QueryResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals

/**
 * One transaction over two schemas on PostgreSQL, where the search path is transactional: a rollback, to a
 * savepoint or of the whole transaction, undoes the schema the connection was set to.
 */
class PostgreSQLTransactionSchemaTest {

    private fun count(result: QueryResult): Long {
        val value = (result.values.next()[0] as Number).toLong()
        while (result.values.hasNext()) result.values.next()
        return value
    }

    @Test
    fun statementsOfAnotherSchemaRunInTheirOwn() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            val connector = JdbcConnector(pg.jdbcUrl, pg.username, pg.password)
            connector.initialize()
            connector.mutate(null, "CREATE SCHEMA shelf")
            connector.mutate(null, "CREATE TABLE shelf.book (title varchar)")
            connector.mutate(null, "CREATE SCHEMA club")
            connector.mutate(null, "CREATE TABLE club.review (stars int)")

            // rolled back as a whole
            var tx = connector.begin("club")
            tx.mutate("shelf", "INSERT INTO book VALUES ('Dune')")
            tx.mutate("club", "INSERT INTO review VALUES (5)")
            tx.rollback()
            assertEquals(0, count(connector.query("shelf", "SELECT count(*) FROM book")))
            assertEquals(0, count(connector.query("club", "SELECT count(*) FROM review")))

            // a savepoint rolled back between two statements of the other schema, then committed
            tx = connector.begin("club")
            tx.mutate("club", "INSERT INTO review VALUES (4)")
            tx.begin("club")
            tx.mutate("shelf", "INSERT INTO book VALUES ('Solaris')")
            tx.rollback()
            tx.mutate("shelf", "INSERT INTO book VALUES ('Ubik')")
            assertEquals(1, count(tx.query("club", "SELECT count(*) FROM review")))
            tx.commit()
            assertEquals(1, count(connector.query("shelf", "SELECT count(*) FROM book WHERE title = 'Ubik'")))
            assertEquals(1, count(connector.query("shelf", "SELECT count(*) FROM book")))

            // the connection went back to its pool on its own schema, after a commit as after a rollback
            repeat(2) {
                tx = connector.begin("club")
                assertEquals(1, count(tx.query("club", "SELECT count(*) FROM review")))
                tx.mutate("shelf", "INSERT INTO book VALUES ('Vurt')")
                tx.rollback()
            }
        }
    }
}
