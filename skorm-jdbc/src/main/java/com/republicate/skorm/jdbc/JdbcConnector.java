package com.republicate.skorm.jdbc;


import com.republicate.skorm.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

public class JdbcConnector implements Connector
{
    private DataSource dataSource = null;
    private ConnectionFactory connectionFactory = null;
    private ConnectionPool connectionPool = null;
    private ConnectionPool txConnectionPool = null;
    private StatementPool statementPool = null;

    private final Configuration config = new Configuration();

    private class TxConnector implements TransactionConnector
    {
        Connection txConnection;
        Deque<Savepoint> savepoints = new LinkedList<>();

        TxConnector() throws SkormException
        {
            try
            {
                // CB TODO - in which schema?!
                txConnection = txConnectionPool.getConnection();
            }
            catch (SQLException sqle)
            {
                throw new SkormException("cannot start transaction", sqle);
            }
        }

        @NotNull
        @Override
        public Configuration getConfig()
        {
            return JdbcConnector.this.config;
        }

        @NotNull
        @Override
        public QueryResult query(@NotNull String schema, @NotNull String query, @Nullable Object... params) throws SkormException
        {
            try
            {
                PooledStatement stmt = statementPool.prepareQuery(schema, query, txConnection);
                ResultSet rs = stmt.executeQuery(params);
                return buildQueryResult(rs, stmt);
            }
            catch (SQLException sqle)
            {
                throw new SkormException("error running query " + shorten(query), sqle);
            }
        }

        @Override
        public long mutate(@NotNull String schema, @NotNull String query, @Nullable Object... params) throws SkormException
        {
            try
            {
                PooledStatement stmt = statementPool.prepareUpdate(schema, query, txConnection);
                long changed = stmt.executeUpdate(params);
                return params.length > 0 && params[params.length - 1] instanceof GeneratedKeyMarker
                        ? stmt.getLastInsertID(((GeneratedKeyMarker) params[params.length - 1]).getColName())
                        : changed;
            }
            catch (SQLException sqle)
            {
                throw new SkormException("error running mutation " + shorten(query), sqle);
            }
        }

        @NotNull
        @Override
        public TransactionConnector begin() throws SkormException
        {
            try
            {
                savepoints.push(txConnection.setSavepoint());
                return this;
            }
            catch (SQLException sqle)
            {
                throw new SkormException("error starting nested transaction", sqle);
            }
        }

        @NotNull
        @Override
        public void commit() throws SkormException
        {
            try
            {
                Savepoint sp = savepoints.poll();
                if (sp == null) txConnection.commit(); else txConnection.releaseSavepoint(sp);
            }
            catch (SQLException sqle)
            {
                throw new SkormException("could not commit transaction", sqle);
            }
        }

        @NotNull
        @Override
        public void rollback() throws SkormException
        {
            try
            {
                Savepoint sp = savepoints.poll();
                if (sp == null) txConnection.rollback(); else txConnection.rollback(sp);
            }
            catch (SQLException sqle)
            {
                throw new SkormException("could not commit transaction", sqle);
            }
        }

        @Override
        public MetaInfos getMetaInfos() throws SkormException
        {
            try
            {
                return txConnection.getVendor();
            }
            catch (SQLException sqle)
            {
                throw new SkormException("could not get meta infos", sqle);
            }
        }
    }

    @NotNull
    @Override
    public Configuration getConfig() {
        return config;
    } // CB TODO - a public method returning a config containing passwords, hum hum...

    @Override
    public String getConfigTag() {
        return "jdbc";
    }

    private String getUrl() {
        return config.getString("url");
    }

    private String getLogin() {
        return config.getString("login");
    }

    private String getPassword() {
        return config.getString("password");
    }

    public String getDriver() {
        return config.getString("driver");
    }

    @Override
    public synchronized void initialize() throws SkormException
    {
        try
        {
            if (dataSource == null)
            {
                if (getDriver() != null)
                {
                    try
                    {
                        Class.forName(getDriver());
                    }
                    catch (ClassNotFoundException cnfe)
                    {
                        throw new SQLException("could not load driver " + getDriver(), cnfe);
                    }
                }
                dataSource = new BasicDataSource(getUrl());
                connectionFactory = new ConnectionFactory(dataSource, getLogin(), getPassword());
                connectionPool = new ConnectionPool(connectionFactory, true);
                txConnectionPool = new ConnectionPool(connectionFactory, false);
                statementPool = new StatementPool(connectionPool);
            }
        }
        catch (SQLException sqle)
        {
            throw new SkormException("could not initialize jdbc connector", sqle);
        }
    }

    public JdbcConnector()
    {
    }

    @Override
    public MetaInfos getMetaInfos() throws SkormException {
        try
        {
            return connectionPool.getConnection().getVendor();
        }
        catch (SQLException sqle)
        {
            throw new SkormException("could not get meta infos", sqle);
        }
    }

    public JdbcConnector(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public JdbcConnector(String url) {
        this(url, null, null, null);
    }

    public JdbcConnector(String url, String user, String password) {
        this(url, user, password, null);
    }

    public JdbcConnector(String url, String user, String password, String driver) {
        config.configure("url", url);
        if (user != null) config.configure("user", user);
        if (password != null) config.configure("password", password);
        if (driver != null) config.configure("driver", driver);
    }

    @NotNull
    @Override
    public QueryResult query(@NotNull String schema, @NotNull String query, @Nullable Object... params) throws SkormException
    {
        try
        {
            PooledStatement stmt = statementPool.prepareQuery(schema, query);
            ResultSet rs = stmt.executeQuery(params);
            return buildQueryResult(rs, stmt);
        }
        catch (SQLException sqle)
        {
            throw new SkormException("error running query " + shorten(query), sqle);
        }
    }

    @Override
    public long mutate(@NotNull String schema, @NotNull String query, @Nullable Object... params) throws SkormException
    {
        try
        {
            PooledStatement stmt = statementPool.prepareUpdate(schema, query);
            long changed = stmt.executeUpdate(params);
            return params.length > 0 && params[params.length - 1] instanceof GeneratedKeyMarker
                    ? stmt.getLastInsertID(((GeneratedKeyMarker) params[params.length - 1]).getColName())
                    : changed;
        }
        catch (SQLException sqle)
        {
            throw new SkormException("error running mutation " + shorten(query), sqle);
        }
    }

    @NotNull
    @Override
    public TransactionConnector begin() throws SkormException
    {
        return new TxConnector();
    }

    private static QueryResult buildQueryResult(ResultSet rs, PooledStatement stmt) throws SQLException
    {
        ResultSetMetaData meta = rs.getMetaData();
        int n = meta.getColumnCount();
        String names[] = new String[n];
        for (int i = 0; i < n; ++i)
        {
            names[i] = meta.getColumnName(i + 1);
        }
        return new QueryResult(names, new RowIterator(rs, stmt));
    }

    private static class RowIterator implements Iterator<Object[]>
    {
        ResultSet resultSet;
        PooledStatement statement;
        boolean prefretch = false;
        boolean over = false;
        int rowSize;

        RowIterator(ResultSet rs, PooledStatement stmt) throws SQLException
        {
            this.resultSet = rs;
            this.statement = stmt;
            this.rowSize = rs.getMetaData().getColumnCount();
        }

        @Override
        public boolean hasNext() // throws SkormException CB TODO - checked exception problem
        {
            boolean ret;
            try
            {
                if (over) return false;
                else if (prefretch) return true;
                else
                {
                    try
                    {
                        statement.getConnection().enterBusyState();
                        ret = resultSet.next();
                    }
                    finally
                    {
                        statement.getConnection().leaveBusyState();
                    }
                    if (ret)
                    {
                        prefretch = true;
                    }
                    else
                    {
                        over = true;
                        statement.notifyOver();
                    }
                }
                return ret;
            }
            catch (SQLException sqle)
            {
                over = true;
                statement.notifyOver();
                throw new RuntimeException("could not fetch next row", sqle);
            }
        }

        @Override
        public Object[] next() // throws SkormException CB TODO - checked exception problem
        {
            try
            {
                if (over || !prefretch && !resultSet.next())
                {
                    if (!over)
                    {
                        over = true;
                        statement.notifyOver();
                    }
                    return null;
                }
                prefretch = false;
                Object[] result = new Object[rowSize];
                for (int i = 0; i < rowSize; ++i)
                {
                    result[i] = resultSet.getObject(i + 1);
                }
                return result;
            }
            catch (SQLException sqle)
            {
                throw new RuntimeException("could not fetch next row", sqle);
            }
        }
    }

    private static String shorten(String qry) {
        if (qry.length() > 50) return qry.substring(0, 50) + "...";
        else return qry;
    }

}
