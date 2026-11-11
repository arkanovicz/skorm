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
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

public class JdbcConnector implements Connector, Closeable
{
    private DataSource dataSource = null;
    private ConnectionFactory connectionFactory = null;
    private ConnectionPool connectionPool = null;
    private ConnectionPool txConnectionPool = null;
    private StatementPool statementPool = null;

    private final Configuration config = new Configuration();

    /** rows per fetch of a streamed query */
    private int fetchSize = DEFAULT_FETCH_SIZE;
    public static final int DEFAULT_FETCH_SIZE = 1000;

    private class TxConnector implements TransactionConnector
    {
        Connection txConnection;
        Deque<Savepoint> savepoints = new LinkedList<>();
        /** the schema the connection is set to: the one it was pooled for, until a statement of another schema comes */
        private String schema;
        /** what the connection reported before its first move, to set it back before it returns to its pool */
        private String home = null;

        TxConnector(@Nullable String schema) throws SkormException
        {
            try
            {
                // handed out busy: held exclusively until terminal commit/rollback
                txConnection = txConnectionPool.getConnection(schema);
                this.schema = schema;
            }
            catch (SQLException sqle)
            {
                throw new SkormException("cannot start transaction", sqle);
            }
        }

        /**
         * A statement resolves its unqualified names in its own schema, in a transaction as outside one:
         * the transaction's single connection follows the statements across schemas.
         */
        private void enter(@Nullable String schema) throws SQLException
        {
            if (schema == null || schema.isEmpty() || schema.equals(this.schema)) return;
            if (home == null) home = txConnection.getSchema();
            txConnection.enterSchema(schema);
            this.schema = schema;
        }

        /** back where the pool expects it; within the transaction when it commits, or the setting would wait for the next one */
        private void goHome() throws SQLException
        {
            if (home == null) return;
            txConnection.setSchema(home);
            home = null;
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
            return read(schema, query, false, params);
        }

        @NotNull
        @Override
        public QueryResult stream(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
        {
            return read(schema, query, true, params);
        }

        /** a transaction's statements are its own, prepared for one use: closed with their result, or on failure */
        private QueryResult read(@Nullable String schema, @NotNull String query, boolean streamed, @Nullable Object[] params) throws SkormException
        {
            PooledStatement stmt = null;
            try
            {
                enter(schema);
                stmt = statementPool.prepareQuery(schema, query, txConnection);
                if (streamed) stmt.setFetchSize(fetchSize);
                PooledStatement opened = stmt;
                return buildQueryResult(stmt.executeQuery(params), stmt, () -> {
                    opened.notifyOver();
                    opened.close();
                    return kotlin.Unit.INSTANCE;
                });
            }
            catch (SQLException sqle)
            {
                if (stmt != null) stmt.close();
                throw new SkormException("error running query " + shorten(query), sqle);
            }
        }

        @Override
        public long mutate(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
        {
            try
            {
                enter(schema);
                PooledStatement stmt = statementPool.prepareUpdate(schema, query, txConnection);
                try
                {
                    long changed = stmt.executeUpdate(params);
                    return params.length > 0 && params[params.length - 1] instanceof GeneratedKeyMarker
                            ? stmt.getLastInsertID(((GeneratedKeyMarker) params[params.length - 1]).getColName())
                            : changed;
                }
                finally
                {
                    stmt.notifyOver();
                    stmt.close();
                }
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
                if (sp == null)
                {
                    goHome();
                    txConnection.commit();
                    txConnection.leaveBusyState();
                }
                else txConnection.releaseSavepoint(sp);
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
                if (sp == null)
                {
                    txConnection.rollback();
                    if (home != null)
                    {
                        // an engine may or may not undo the setting with the transaction
                        goHome();
                        txConnection.commit();
                    }
                    txConnection.leaveBusyState();
                }
                else
                {
                    txConnection.rollback(sp);
                    // PostgreSQL undoes a schema set since the savepoint: no longer known, set again by the next statement
                    if (home != null) schema = null;
                }
            }
            catch (SQLException sqle)
            {
                throw new SkormException("could not rollback transaction", sqle);
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
            }
            // a data source handed to the constructor needs its pools too
            if (connectionFactory == null)
            {
                connectionFactory = new ConnectionFactory(dataSource, getLogin(), getPassword());
                connectionPool = new ConnectionPool(connectionFactory, true);
                txConnectionPool = new ConnectionPool(connectionFactory, false);
                statementPool = new StatementPool(connectionPool);
                Integer configured = config.getInt("fetchSize");
                if (configured != null) fetchSize = configured;
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
            Connection connection = connectionPool.getConnection();
            try
            {
                return connection.getVendor();
            }
            finally
            {
                connection.leaveBusyState();
            }
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
        if (user != null) config.configure("login", user);
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

    /**
     * Many rows, fetched as the reader goes: the driver only streams a forward-only result set with autocommit off,
     * so the query runs on a connection of the transaction pool, held until the reader closes the result, then
     * committed and released. Single-row reads stay on the shared, autocommit statements: no commit round trip.
     */
    @NotNull
    @Override
    public QueryResult stream(@Nullable String schema, @NotNull String query, @Nullable Object... params) throws SkormException
    {
        if (schema == null) {
            schema = config.getString("defaultSchema");
        }
        Connection connection;
        try
        {
            connection = txConnectionPool.getConnection(schema);
        }
        catch (SQLException sqle)
        {
            throw new SkormException("error running query " + shorten(query), sqle);
        }
        PooledStatement stmt = null;
        try
        {
            stmt = statementPool.prepareQuery(schema, query, connection);
            stmt.setFetchSize(fetchSize);
            ResultSet rs = stmt.executeQuery(params);
            PooledStatement opened = stmt;
            return buildQueryResult(rs, stmt, () -> {
                opened.notifyOver();
                opened.close();
                try
                {
                    connection.commit();
                }
                catch (SQLException sqle)
                {
                    throw new RuntimeException("could not end the read", sqle);
                }
                finally
                {
                    connection.leaveBusyState();
                }
                return kotlin.Unit.INSTANCE;
            });
        }
        catch (SQLException sqle)
        {
            if (stmt != null) stmt.close();
            try
            {
                connection.rollback();
            }
            catch (SQLException ignored) {}
            connection.leaveBusyState();
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
            try
            {
                long changed = stmt.executeUpdate(params);
                return params.length > 0 && params[params.length - 1] instanceof GeneratedKeyMarker
                        ? stmt.getLastInsertID(((GeneratedKeyMarker) params[params.length - 1]).getColName())
                        : changed;
            }
            finally
            {
                // back to the pool: without this every mutate prepared a fresh statement, kept for good
                stmt.notifyOver();
            }
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
        return buildQueryResult(rs, stmt, () -> { stmt.notifyOver(); return kotlin.Unit.INSTANCE; });
    }

    private static QueryResult buildQueryResult(ResultSet rs, PooledStatement stmt, kotlin.jvm.functions.Function0<kotlin.Unit> closer) throws SQLException
    {
        ResultSetMetaData meta = rs.getMetaData();
        int n = meta.getColumnCount();
        String names[] = new String[n];
        String types[] = new String[n];
        for (int i = 0; i < n; ++i)
        {
            names[i] = meta.getColumnName(i + 1);
            types[i] = meta.getColumnTypeName(i + 1);
        }
        return new QueryResult(names, new RowIterator(rs, stmt), types, closer);
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
        Class<?>[] readAs; // JDBC 4.2 java.time class per column, null for the driver's default

        RowIterator(ResultSet rs, PooledStatement stmt) throws SQLException
        {
            this.resultSet = rs;
            this.statement = stmt;
            ResultSetMetaData meta = rs.getMetaData();
            this.rowSize = meta.getColumnCount();
            this.readAs = new Class<?>[rowSize];
            for (int i = 0; i < rowSize; ++i)
            {
                readAs[i] = readAs(meta.getColumnType(i + 1), meta.getColumnTypeName(i + 1));
            }
        }

        // The default getObject gives java.sql.Date/Time/Timestamp, epoch millis in the JVM's zone: a wall clock in a DST gap
        // shifts by an hour, and PostgreSQL reports timestamptz as a plain TIMESTAMP, so an instant shifts too
        private static Class<?> readAs(int type, String typeName)
        {
            if ("timestamptz".equalsIgnoreCase(typeName)) return OffsetDateTime.class;
            switch (type)
            {
                case Types.TIMESTAMP_WITH_TIMEZONE: return OffsetDateTime.class;
                case Types.TIMESTAMP: return LocalDateTime.class;
                case Types.DATE: return LocalDate.class;
                case Types.TIME: return LocalTime.class;
                default: return null;
            }
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
                    result[i] = ClassMapper.read(readAs[i] == null ? resultSet.getObject(i + 1) : resultSet.getObject(i + 1, readAs[i]));
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
