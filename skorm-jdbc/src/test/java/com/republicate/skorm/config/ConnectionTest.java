package com.republicate.skorm.config;

import com.republicate.skorm.jdbc.BasicDataSource;
import com.republicate.skorm.jdbc.Connection;
import com.republicate.skorm.jdbc.ConnectionFactory;
import com.republicate.skorm.jdbc.ConnectionPool;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
