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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Connection pool.
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class ConnectionPool
{
    protected static Logger logger = LoggerFactory.getLogger("sql");

    private ConnectionFactory connectionFactory;

    /** autocommit flag */
    private boolean autocommit = true;

    /** list of all connections, per schema */
    private Map<String, List<Connection>> connectionsMap = new ConcurrentHashMap<>();

    /** Maximum number of connections (per schema). */
    private int max;

    /** Maximum wait for a connection when all are busy, in milliseconds. */
    private long timeout;

    /**
     * Constructor.
     * @param connectionFactory
     * @throws SQLException
     */
    public ConnectionPool(ConnectionFactory connectionFactory) throws SQLException
    {
        this(connectionFactory, true);
    }

    /**
     * Constructor.
     * @param connectionFactory
     * @param autocommit
     * @throws SQLException
     */
    public ConnectionPool(ConnectionFactory connectionFactory, boolean autocommit) throws SQLException
    {
        this(connectionFactory, autocommit, -1);
    }

    /**
     * Constructor.
     * @param connectionFactory
     * @param autocommit
     * @param max
     * @throws SQLException
     */
    public ConnectionPool(ConnectionFactory connectionFactory, boolean autocommit, int max) throws SQLException
    {
        this(connectionFactory, autocommit, max, 30000);
    }

    /**
     * Constructor.
     * @param connectionFactory
     * @param autocommit
     * @param max
     * @param timeout milliseconds to wait for a free connection once max is reached
     * @throws SQLException
     */
    public ConnectionPool(ConnectionFactory connectionFactory, boolean autocommit, int max, long timeout) throws SQLException
    {
        this.connectionFactory = connectionFactory;
        this.autocommit = autocommit;
        this.max = max;
        this.timeout = timeout;
    }

    public Connection getConnection() throws SQLException
    {
        return getConnection("");
    }

    /**
     * Get a connection, in busy state: the caller must call leaveBusyState() once done with it.
     * @return a connection
     * @throws SQLException
     */
    public synchronized Connection getConnection(@Nullable String schema) throws SQLException
    {
        if (schema == null) {
            schema = "";
        }
        List<Connection> connections = connectionsMap.computeIfAbsent(schema, (s) -> new ArrayList<>());
        long deadline = System.currentTimeMillis() + timeout;
        while (true)
        {
            for(Iterator it = connections.iterator(); it.hasNext(); )
            {
                Connection c = (Connection)it.next();

                if(c.isClosed())
                {
                    it.remove();
                }
                else if(!c.isBusy())
                {
                    c.enterBusyState();
                    return c;
                }
            }
            if(connections.size() != max)
            {
                break;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
            {
                throw new SQLException("connection pool: no connection available after " + timeout + " ms");
            }
            try
            {
                wait(remaining);
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
                throw new SQLException("connection pool: interrupted while waiting for a connection", ie);
            }
        }

        Connection newconn = createConnection();
        if (!schema.isEmpty())
        {
            switch (newconn.getVendor().getIdentifierInternalCase()) {
                case 'L': schema = schema.toLowerCase(Locale.ROOT); break;
                case 'U': schema = schema.toUpperCase(Locale.ROOT); break;
            }
            newconn.setSchema(schema);
        }
        newconn.enterBusyState();
        connections.add(newconn);
        return newconn;
    }

    /**
     * Create a connection.
     *
     * @return connection
     * @throws SQLException
     */
    private Connection createConnection() throws SQLException
    {
        logger.info("Creating a new connection");
        Connection connection = connectionFactory.newConnection();
        connection.setAutoCommit(autocommit);
        connection.setPool(this);
        return connection;
    }

    /**
     * Wake up callers waiting for a connection.
     */
    synchronized void release()
    {
        notifyAll();
    }

    /**
     * clear all connections.
     */
    public void clear()
    {
        for(Iterator<List<Connection>> it = connectionsMap.values().iterator(); it.hasNext(); )
        {
            for (Iterator<Connection> jt = it.next().iterator(); jt.hasNext(); ) {
                Connection c = jt.next();
                try
                {
                    c.close();
                }
                catch (SQLException sqle) {}
            }
        }
    }
}
