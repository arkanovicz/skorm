package com.republicate.skorm.config;

import com.republicate.skorm.jdbc.*;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

public class StatementTest extends ConnectionTest
{
    @Test
    public void testBasicStatement() throws Exception
    {
        StatementPool statementPool = new StatementPool("test", testConnectionPool());
        PooledStatement statement = statementPool.prepareQuery("SELECT 1");
        ResultSet rs = statement.executeQuery();
        assertTrue(rs.next());
        int value = rs.getInt(1);
        assertEquals(1, value);
        assertEquals(true, statement.isInUse());
        statement.notifyOver();
        assertEquals(false, statement.isInUse());
    }

    @Test
    public void testStatementReuse() throws Exception
    {
        StatementPool statementPool = new StatementPool("test", testConnectionPool());
        PooledStatement statement = statementPool.prepareQuery("SELECT 1");
        ResultSet rs = statement.executeQuery();
        assertTrue(rs.next());
        int value = rs.getInt(1);
        assertEquals(1, value);
        assertEquals(true, statement.isInUse());
        statement.notifyOver();
        assertEquals(false, statement.isInUse());
        PooledStatement statement2 = statementPool.prepareQuery("SELECT 1");
        assertTrue(statement == statement2);
    }

    @Test
    public void testStatementInUse() throws Exception
    {
        StatementPool statementPool = new StatementPool("test", testConnectionPool());
        PooledStatement statement = statementPool.prepareQuery("SELECT 1");
        ResultSet rs = statement.executeQuery();
        assertTrue(rs.next());
        int value = rs.getInt(1);
        assertEquals(1, value);
        assertEquals(true, statement.isInUse());
        PooledStatement statement2 = statementPool.prepareQuery("SELECT 1");
        assertFalse(statement == statement2);
        statement.notifyOver();
        statement2.notifyOver();
    }
}
