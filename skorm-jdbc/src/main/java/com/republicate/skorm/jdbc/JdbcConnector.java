package com.republicate.skorm.jdbc;


import com.republicate.skorm.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.*;

public class JdbcConnector implements Connector, Closeable
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

        TxConnector(@Nullable String schema) throws SkormException
        {
            try
            {
                txConnection = txConnectionPool.getConnection(schema);
            }
            catch (SQLException sqle)
            {
                throw new SkormException("cannot start transaction", sqle);
            }
        }

        /* TODO Kotlin default interface methods not seen?! */

        @Override
        public String getConfigTag() {
            return null;
        }

        @Override
        public void configure(Map<String, ? extends Object> cfg) {
            // NOP
        }

        @Override
        public void initialize() {
            // NOP
        }

        @Override
        public void initialize(Map<String, ? extends Object> cfg) {
            // NOP
        }

        @Override
        public void close() {
            // NOP
        }

        @NotNull
        @Override
        public Configuration getConfig()
        {
            return JdbcConnector.this.config;
        }

        @NotNull
        @Override
        public QueryResult query(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
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
        public long mutate(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
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
        public TransactionConnector begin(@Nullable String schema) throws SkormException
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
    public void initialize(@Nullable Map<String, ? extends Object> cfg) throws SkormException {
        if (cfg != null) {
            configure(cfg);
        }
        initialize();
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

    public JdbcConnector() {
    }

    public JdbcConnector(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public JdbcConnector(String url) {
        this(url, null, null, null, null);
    }

    public JdbcConnector(String url, String user, String password) {
        this(url, user, password, null, null);
    }

    public JdbcConnector(String url, String user, String password, String driver) {
        this(url, user, password, driver, null);
    }

    public JdbcConnector(String url, String user, String password, String driver, @Nullable String defaultSchema) {
        config.configure("url", url);
        if (user != null) config.configure("user", user);
        if (password != null) config.configure("password", password);
        if (driver != null) config.configure("driver", driver);
        if (defaultSchema != null) config.configure("defaultSchema", defaultSchema);
    }

    @Override
    public void configure(Map<String, ? extends Object> cfg) {
        config.configure(cfg);
    }

    @NotNull
    @Override
    public QueryResult query(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
    {
        try
        {
            if (schema == null) {
                schema = config.getString("defaultSchema");
            }
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
    public long mutate(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
    {
        try
        {
            if (schema == null) {
                schema = config.getString("defaultSchema");
            }
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
    public TransactionConnector begin(@Nullable String schema) throws SkormException
    {
        return new TxConnector(schema);
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

    @Override
    public void close() {
        statementPool.close();
        txConnectionPool.clear();
        connectionPool.clear();
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
                    result[i] = ClassMapper.read(resultSet.getObject(i + 1));
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
