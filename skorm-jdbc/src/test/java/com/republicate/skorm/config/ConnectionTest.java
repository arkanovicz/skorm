package com.republicate.skorm.config;

import com.republicate.skorm.jdbc.BasicDataSource;
import com.republicate.skorm.jdbc.Connection;
import com.republicate.skorm.jdbc.ConnectionFactory;
import com.republicate.skorm.jdbc.ConnectionPool;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionTest
{
    @Test
    public DataSource testCreateBasicDataSource() throws Exception
    {
        return new BasicDataSource("jdbc:h2:mem:test");
    }

    @Test
    public ConnectionPool testConnectionPool() throws Exception
    {
        ConnectionFactory factory = new ConnectionFactory(testCreateBasicDataSource());
        return new ConnectionPool(factory);
    }

    @Test
    public void testConnection() throws Exception
    {
        ConnectionPool connectionPool = testConnectionPool();
        Connection connection = connectionPool.getConnection();
        assertEquals(true, connection.getAutoCommit());
    }

    @Test
    public void testExhaustedPoolWaits() throws Exception
    {
        ConnectionFactory factory = new ConnectionFactory(new BasicDataSource("jdbc:h2:mem:exhausted"));
        ConnectionPool connectionPool = new ConnectionPool(factory, true, 1, 300);
        Connection connection = connectionPool.getConnection();

        long start = System.currentTimeMillis();
        assertThrows(SQLException.class, connectionPool::getConnection);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 300 && elapsed < 2000, "waited " + elapsed + " ms");

        Thread releaser = new Thread(() -> {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException ie) {}
            connection.leaveBusyState();
        });
        releaser.start();
        assertSame(connection, connectionPool.getConnection());
        releaser.join();
    }

    @Test
    public void testHandedOutConnectionIsBusy() throws Exception
    {
        ConnectionFactory factory = new ConnectionFactory(new BasicDataSource("jdbc:h2:mem:handout"));
        ConnectionPool connectionPool = new ConnectionPool(factory);
        Connection first = connectionPool.getConnection();
        assertTrue(first.isBusy());
        assertNotSame(first, connectionPool.getConnection());
        first.leaveBusyState();
        assertSame(first, connectionPool.getConnection());
    }
}
