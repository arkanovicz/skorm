package com.republicate.skorm.jdbc

import com.republicate.kson.Json
import com.republicate.skorm.ValuesFiltering
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A kson `Json` value binds to a json/jsonb column as its text, and the column reads back as a `Json` through the stock
 * `parseJson` filter (the one skorm-core applies to json/jsonb columns), whatever the driver hands over:
 * a PGobject on PostgreSQL, the text as bytes on H2.
 */
class JsonTest {

    private val reactions = Json.parse("""[{"id":"tea","count":2},{"id":"coffee","count":0}]""")!!
    private val parseJson = ValuesFiltering["parseJson"]!!

    private fun JdbcConnector.roundTrip(jsonType: String, storedAsJson: Boolean) {
        initialize()
        try {
            mutate(null, "CREATE TABLE community (id INT PRIMARY KEY, reactions $jsonType)")
            mutate(null, "INSERT INTO community (id, reactions) VALUES (1, ?)", reactions)
            query(null, "SELECT reactions FROM community WHERE id = 1").use {
                val read = parseJson(it.values.next()[0])
                assertIs<Json.Array>(read)
                assertEquals(reactions.toString(), read.toString())
            }
            if (storedAsJson) query(null, "SELECT CAST(reactions AS VARCHAR) FROM community WHERE id = 1").use {
                val text = it.values.next()[0].toString()
                assertEquals('[', text.first(), "stored as $text")
            }
        } finally {
            close()
        }
    }

    // H2 stores a bound String as a JSON string (only bytes or `? FORMAT JSON` land as JSON): the read filter unwraps it
    @Test fun h2() = JdbcConnector("jdbc:h2:mem:json;DB_CLOSE_DELAY=-1").roundTrip("JSON", storedAsJson = false)

    private fun postgresql(jsonType: String) {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            JdbcConnector(pg.jdbcUrl, pg.username, pg.password).roundTrip(jsonType, storedAsJson = true)
        }
    }

    @Test fun postgresqlJson() = postgresql("json")
    @Test fun postgresqlJsonb() = postgresql("jsonb")
}
