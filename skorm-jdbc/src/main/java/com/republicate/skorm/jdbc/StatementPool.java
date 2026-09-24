package com.republicate.skorm.jdbc;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is a pool of PooledPreparedStatements.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class StatementPool implements Closeable
{
    protected Logger logger = LoggerFactory.getLogger("jdbc");

    public StatementPool(ConnectionPool connectionPool)
    {
        this(connectionPool, -1);
    }

    /**
     * build a new pool.
     *
     * @param connectionPool connection pool
     */
    public StatementPool(ConnectionPool connectionPool, long connectionsCheckInterval)
    {
        this(connectionPool, connectionsCheckInterval, DEFAULT_MAX_STATEMENTS);
    }

    /**
     * @param maxStatements how many shared statements stay prepared: past it, the least recently used idle one is closed
     */
    public StatementPool(ConnectionPool connectionPool, long connectionsCheckInterval, int maxStatements)
    {
        this.connectionPool = connectionPool;
        this.connectionsCheckInterval = connectionsCheckInterval;
        this.maxStatements = maxStatements;
    }

    /**
     * get a PooledStatement associated with this query.
     *
     * @param query an SQL query
     * @exception SQLException thrown by the database engine
     * @return a valid statement
     */
    protected synchronized PooledStatement prepareStatement(@Nullable String schema, String query, boolean update, Connection connection) throws SQLException
    {
        logger.trace("prepare-{}", query);

        if (schema == null) {
            schema = "";
        }
        PooledStatement statement;
        List<PooledStatement> availableStatements = null;
        boolean sharedStatement = connection == null;
        if (sharedStatement)
        {
            availableStatements = statementsMap.computeIfAbsent(Pair.of(schema, query), (str) -> new ArrayList<>());
            for (Iterator<PooledStatement> it = availableStatements.iterator(); it.hasNext(); )
            {
                statement = it.next();
                if (statement.isValid())
                {
                    if (!statement.isInUse() && !(connection = statement.getConnection()).isBusy())
                    {
                        // check connection
                        if (!connection.isClosed() && (connectionsCheckInterval < 0 || System.currentTimeMillis() - connection.getLastUse() < connectionsCheckInterval || connection.check()))
                        {
                            statement.notifyInUse();
                            return statement;
                        }
                        else
                        {
                            dropConnection(connection);
                            it.remove();
                        }
                    }
                }
                else
                {
                    it.remove();
                }
            }
            makeRoom();
            // makeRoom may have dropped this query's emptied list
            availableStatements = statementsMap.computeIfAbsent(Pair.of(schema, query), (str) -> new ArrayList<>());
            connection = connectionPool.getConnection(schema);
        }

        try
        {
            statement = new PooledStatement(connection,
                    update ?
                        connection.prepareStatement(
                                query, connection.getVendor().getLastInsertIdPolicy() == Vendor.LastInsertIdPolicy.GENERATED_KEYS ?
                                        Statement.RETURN_GENERATED_KEYS :
                                        Statement.NO_GENERATED_KEYS) :
                        connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
        }
        finally
        {
            // a shared statement's connection is only held while executing
            if (sharedStatement)
            {
                connection.leaveBusyState();
            }
        }
        if (sharedStatement)
        {
            availableStatements.add(statement);
        }
        statement.notifyInUse();
        return statement;
    }

    public synchronized PooledStatement prepareQuery(String query) throws SQLException
    {
        return prepareQuery(null, query);
    }

    public synchronized PooledStatement prepareQuery(@Nullable String schema, String query) throws SQLException
    {
        return prepareStatement(schema, query, false, null);
    }

    public synchronized PooledStatement prepareQuery(String query, Connection txConnection) throws SQLException
    {
        return prepareQuery(null, query, txConnection);
    }

    public synchronized PooledStatement prepareQuery(@Nullable String schema, String query, Connection txConnection) throws SQLException
    {
        return prepareStatement(schema, query, false, txConnection);
    }

    public synchronized PooledStatement prepareUpdate(String query) throws SQLException
    {
        return prepareUpdate(null, query);
    }

    public synchronized PooledStatement prepareUpdate(@Nullable String schema, String query) throws SQLException
    {
        return prepareStatement(schema, query, true, null);
    }

    public synchronized PooledStatement prepareUpdate(String query, Connection txConnection) throws SQLException
    {
        return prepareUpdate(null, query, txConnection);
    }

    public synchronized PooledStatement prepareUpdate(@Nullable String schema, String query, Connection txConnection) throws SQLException
    {
        return prepareStatement(schema, query, true, txConnection);
    }

    /**
     * Keeps the shared statements under maxStatements, a bound on the cache, not on concurrency: the invalid ones are
     * dropped, then the least recently used idle ones closed. Statements in use are never closed under their reader;
     * when all are, the cache goes over until they are released (how many run at once is the connection pool's call).
     */
    private void makeRoom()
    {
        int total = 0;
        for (Iterator<List<PooledStatement>> it = statementsMap.values().iterator(); it.hasNext(); )
        {
            List<PooledStatement> statements = it.next();
            statements.removeIf(statement -> !statement.isValid());
            if (statements.isEmpty()) it.remove();
            else total += statements.size();
        }
        while (total >= maxStatements)
        {
            PooledStatement lru = null;
            List<PooledStatement> owner = null;
            for (List<PooledStatement> statements : statementsMap.values())
            {
                for (PooledStatement statement : statements)
                {
                    if (!statement.isInUse() && (lru == null || statement.getTagTime() < lru.getTagTime()))
                    {
                        lru = statement;
                        owner = statements;
                    }
                }
            }
            if (lru == null) break;
            owner.remove(lru);
            lru.close();
            --total;
        }
    }

    /**
     * close all statements.
     */
    public synchronized void clear()
    {
        // close all statements
        for(Iterator<Pair<String, String>> it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for(Iterator<PooledStatement> jt = statementsMap.get(it.next()).iterator(); jt.hasNext(); )
            {
                try
                {
                    jt.next().close();
                }
                catch(Exception e)
                {    // don't care now...
                    logger.warn("error while clearing pool", e);
                }
            }
        }
        statementsMap.clear();
    }

    /*
     *  drop all statements relative to a specific connection
     * @param connection the connection
     */
    private void dropConnection(Connection connection)
    {
        for(Iterator<Pair<String, String>> it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for (PooledStatement statement : statementsMap.get(it.next()))
            {
                if (statement.getConnection() == connection)
                {
                    try
                    {
                        statement.close();
                    }
                    catch (Exception e)
                    {
                        logger.warn("error while dropping connection", e);
                    }
                    statement.setInvalid();
                }
            }
        }
        try
        {
            connection.close();
        }
        catch(SQLException ignored) {}
    }

    /**
     * clear statements closing.
     */
    @Override
    public void close()
    {
        clear();
    }

    /**
     * debug - get usage statistics.
     *
     * @return an int array : [nb of statements in use , total nb of statements]
     */
    public synchronized int[] getUsageStats()
    {
        int[] stats = new int[] { 0, 0 };
        for (List<PooledStatement> statements : statementsMap.values())
        {
            for (PooledStatement pooledStatement : statements)
            {
                if (pooledStatement.isInUse())
                {
                    stats[0]++;
                }
                stats[1]++;
            }
        }
        return stats;
    }

    /**
     * connection pool.
     */
    private final ConnectionPool connectionPool;

    /**
     * map queries -&gt; statements.
     */
    private final Map<Pair<String, String>,List<PooledStatement>> statementsMap = new HashMap<>();    // query -> PooledStatement

    /**
     * connections check interval
     */
    private long connectionsCheckInterval;

    /**
     * max number of shared statements kept prepared.
     */
    private final int maxStatements;

    /** the PostgreSQL driver's own per-connection cache default */
    public static final int DEFAULT_MAX_STATEMENTS = 256;
}
