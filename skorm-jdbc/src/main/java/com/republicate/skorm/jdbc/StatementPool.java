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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class StatementPool
{
    protected Logger logger = LoggerFactory.getLogger("jdbc");

    public StatementPool(String modelId, ConnectionPool connectionPool)
    {
        this(modelId, connectionPool, -1);
    }

    /**
     * build a new pool.
     *
     * @param connectionPool connection pool
     */
    public StatementPool(String modelId, ConnectionPool connectionPool, long connectionsCheckInterval)
    {
        this.modelId = modelId;
        this.connectionPool = connectionPool;
        this.connectionsCheckInterval = connectionsCheckInterval;
    }

    /**
     * get a PooledStatement associated with this query.
     *
     * @param query an SQL query
     * @exception SQLException thrown by the database engine
     * @return a valid statement
     */
    protected synchronized PooledStatement prepareStatement(String query, boolean update) throws SQLException
    {
        logger.trace("prepare-{}", query);

        PooledStatement statement;
        Connection connection = getCurrentTransactionConnection(modelId);
        boolean insideTransaction = false;
        List<PooledStatement> availableStatements = statementsMap.computeIfAbsent(query, (str) -> new ArrayList<>());

        if (connection == null)
        {
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
            if (count == maxStatements)
            {
                throw new SQLException("Error: Too many opened prepared statements!");
            }
            connection = connectionPool.getConnection();
        }
        else
        {
            insideTransaction = true;
        }

        statement = new PooledStatement(connection,
                update ?
                    connection.prepareStatement(
                            query, connection.getVendor().getLastInsertIdPolicy() == Vendor.LastInsertIdPolicy.GENERATED_KEYS ?
                                    Statement.RETURN_GENERATED_KEYS :
                                    Statement.NO_GENERATED_KEYS) :
                    connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
        if (!insideTransaction)
        {
            availableStatements.add(statement);
        }
        statement.notifyInUse();
        return statement;
    }

    public synchronized PooledStatement prepareQuery(String query) throws SQLException
    {
        return prepareStatement(query, false);
    }

    public synchronized PooledStatement prepareUpdate(String query) throws SQLException
    {
        return prepareStatement(query, true);
    }

    /**
     * close all statements.
     */
    public void clear()
    {
        // close all statements
        for(Iterator<String> it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for(Iterator<PooledStatement> jt = statementsMap.get(it.next()).iterator(); jt.hasNext(); )
            {
                try
                {
                    jt.next().close();
                }
                catch(SQLException e)
                {    // don't care now...
                    logger.error("error while clearing pool", e);
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
        for(Iterator<String> it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for (PooledStatement statement : statementsMap.get(it.next()))
            {
                if (statement.getConnection() == connection)
                {
                    try
                    {
                        statement.close();
                    }
                    catch (SQLException ignored)
                    {
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
     * clear statements on exit.
     */
    @Override
    protected void finalize()
    {
        // CB TODO - use Cleaner
        clear();
    }

    public static void setCurrentTransactionConnection(String modelId, Connection connection)
    {
        currentTransactionConnection.get().put(modelId, connection);
    }

    public static void resetCurrentTransactionConnection(String modelId)
    {
        currentTransactionConnection.get().remove(modelId);
    }

    public static Connection getCurrentTransactionConnection(String modelId)
    {
        return currentTransactionConnection.get().get(modelId);
    }

    /**
     * debug - get usage statistics.
     *
     * @return an int array : [nb of statements in use , total nb of statements]
     */
    public int[] getUsageStats()
    {
        int[] stats = new int[] { 0, 0 };

        for(Iterator<String> it = statementsMap.keySet().iterator(); it.hasNext(); )
        {
            for (PooledStatement pooledStatement : statementsMap.get(it.next()))
            {
                if (!pooledStatement.isInUse())
                {
                    stats[0]++;
                }
            }
        }
        stats[1] = statementsMap.size();
        return stats;
    }

    /**
     * connection pool.
     */
    private final ConnectionPool connectionPool;

    /**
     * statements getCount.
     */
    private int count = 0;

    /**
     * map queries -&gt; statements.
     */
    private final Map<String,List<PooledStatement>> statementsMap = new HashMap<>();    // query -> PooledStatement

    /**
     * running thread.
     */
    private Thread checkTimeoutThread = null;

    /**
     * true if running.
     */
    private boolean running = true;

    /**
     * connections check interval
     */
    private long connectionsCheckInterval;

    /**
     * model id
     */
    private String modelId;

    /**
     * max number of statements.
     */
    private static final int maxStatements = 50;

    /**
     * current transaction connection
     */
    private static ThreadLocal<Map<String, Connection>> currentTransactionConnection = ThreadLocal.withInitial(ConcurrentHashMap::new);
}
