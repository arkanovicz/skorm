package com.republicate.skorm.jdbc

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records the statements prepared on its connections that are not closed yet. */
private class CountingDataSource(private val inner: DataSource) : DataSource by inner {
    val open: MutableSet<Any> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun getConnection(): java.sql.Connection = wrap(inner.connection)
    override fun getConnection(user: String?, password: String?): java.sql.Connection = wrap(inner.getConnection(user, password))

    private fun wrap(connection: java.sql.Connection) = proxy(connection, java.sql.Connection::class.java) { method, result ->
        if (method.name == "prepareStatement") statement(result as PreparedStatement) else result
    }

    private fun statement(statement: PreparedStatement): PreparedStatement =
        proxy(statement, PreparedStatement::class.java) { method, result ->
            if (method.name == "close") open.remove(statement)
            result
        }.also { open.add(statement) }

    private fun <T> proxy(target: T, type: Class<T>, after: (Method, Any?) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            val result = try {
                method.invoke(target, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
            after(method, result)
        })
}

class StatementAccountingTest {

    private val source = CountingDataSource(BasicDataSource("jdbc:h2:mem:accounting;DB_CLOSE_DELAY=-1"))

    @Test
    fun aConnectorOverADataSourceInitializesItsPools() {
        JdbcConnector(source).use { connector ->
            connector.initialize()
            assertEquals(1, connector.query(null, "SELECT 1").use { (it.values.next()[0] as Number).toInt() })
        }
    }

    @Test
    fun aTransactionClosesItsStatements() {
        JdbcConnector(source).use { connector ->
            connector.initialize()
            connector.mutate(null, "CREATE TABLE IF NOT EXISTS T (ID INT PRIMARY KEY)")
            connector.mutate(null, "DELETE FROM T")
            // the shared statements above stay prepared, by design: only the transaction's must not add to them
            val before = source.open.size
            val tx = connector.begin(null)
            tx.mutate("", "INSERT INTO T VALUES (1)")
            tx.query("", "SELECT ID FROM T").use { it.values.next() }
            tx.stream("", "SELECT ID FROM T").use { it.values.next() }
            tx.commit()
            assertEquals(before, source.open.size, "a transaction's statements are closed once used")
        }
    }

    @Test
    fun theSharedStatementsAreBoundedLeastRecentlyUsedFirst() {
        val statements = StatementPool(ConnectionPool(ConnectionFactory(source), true), -1, 3)
        val before = source.open.size
        repeat(10) { i -> statements.prepareQuery("SELECT $i").apply { executeQuery().close(); notifyOver() } }
        assertEquals(3, statements.usageStats[1], "the cache keeps maxStatements")
        assertEquals(before + 3, source.open.size, "the evicted statements are closed")
        val recent = statements.prepareQuery("SELECT 9")
        recent.notifyOver()
        statements.prepareQuery("SELECT 10").notifyOver()
        assertTrue(statements.prepareQuery("SELECT 9") === recent, "the most recently used one survived the next eviction")
        statements.close()
    }

    @Test
    fun aStatementInUseIsNeverEvicted() {
        val statements = StatementPool(ConnectionPool(ConnectionFactory(source), true), -1, 2)
        val held = statements.prepareQuery("SELECT 100")
        val rows = held.executeQuery()
        repeat(5) { i -> statements.prepareQuery("SELECT $i").apply { executeQuery().close(); notifyOver() } }
        assertTrue(rows.next(), "the reader's statement is still open")
        assertEquals(100, rows.getInt(1))
        held.notifyOver()
        assertTrue(statements.usageStats[1] <= 3, "past the bound only while a statement was in use")
        statements.close()
    }
}
