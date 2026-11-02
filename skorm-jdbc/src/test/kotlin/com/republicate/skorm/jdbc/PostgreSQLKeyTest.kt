package com.republicate.skorm.jdbc

import com.republicate.skorm.Database
import com.republicate.skorm.Entity
import com.republicate.skorm.Field
import com.republicate.skorm.MutableDatabase
import com.republicate.skorm.MutableEntity
import com.republicate.skorm.MutableInstanceImpl
import com.republicate.skorm.Schema
import com.republicate.skorm.MutableSchema
import com.republicate.skorm.core.CoreProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fetch, update and delete by primary key on PostgreSQL, the key given as a String (a REST path) or a Long,
 * for key types whose kddl name is no PostgreSQL type: the parameters go uncast, PostgreSQL types them from the column.
 */
class PostgreSQLKeyTest {

    private class Db(processor: CoreProcessor) : Database("store", processor), MutableDatabase {
        override val readOnly: Database by lazy { this }
    }
    private class Sch(db: Database) : Schema("keys", db), MutableSchema
    private class Ent(name: String, schema: Schema, keyType: String) : Entity(name, schema), MutableEntity {
        init {
            addField(Field("id", keyType, isPrimary = true))
            addField(Field("label", "varchar"))
        }
        override fun new() = MutableInstanceImpl(this)
    }

    private suspend fun Ent.roundTrip(key: Any) {
        val row = fetch(key) as MutableInstanceImpl
        assertEquals("before", row["label"], "$name by ${key::class.simpleName}")
        row["label"] = "after"
        row.update()
        assertEquals("after", fetch(key)?.get("label"))
        row.delete()
        assertNull(fetch(key))
    }

    @Test
    fun keysBindUncast() {
        assumeTrue(System.getenv("SKORM_SKIP_PG_TESTS") == null, "SKORM_SKIP_PG_TESTS set")
        assumeTrue(System.getenv("DOCKER_HOST") != null || java.io.File("/var/run/docker.sock").exists(), "no Docker on this host")
        DockerClientFactory.instance().client()
        PostgreSQLContainer("postgres:16-alpine").use { pg ->
            pg.start()
            val connector = JdbcConnector(pg.jdbcUrl, pg.username, pg.password)
            connector.initialize()
            connector.mutate(null, "CREATE SCHEMA keys")
            // kddl's tinyint is a smallint on PostgreSQL, its double a double precision
            connector.mutate(null, "CREATE TABLE keys.tiny (id smallint PRIMARY KEY, label varchar)")
            connector.mutate(null, "CREATE TABLE keys.dbl (id double precision PRIMARY KEY, label varchar)")
            connector.mutate(null, "INSERT INTO keys.tiny VALUES (3, 'before'), (4, 'before')")
            connector.mutate(null, "INSERT INTO keys.dbl VALUES (3.5, 'before')")
            val db = Db(CoreProcessor(connector))
            val schema = Sch(db)
            val tiny = Ent("tiny", schema, "tinyint")
            val dbl = Ent("dbl", schema, "double")
            db.initialize()
            runBlocking {
                tiny.roundTrip("3")
                tiny.roundTrip(4L)
                dbl.roundTrip("3.5")
            }
            db.close()
        }
    }
}
