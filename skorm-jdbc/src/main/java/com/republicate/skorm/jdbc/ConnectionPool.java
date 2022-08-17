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
        this.connectionFactory = connectionFactory;
        this.autocommit = autocommit;
        this.max = max;
    }

    public Connection getConnection() throws SQLException
    {
        return getConnection("");
    }

    /**
     * Get a connection.
     * @return a connection
     * @throws SQLException
     */
    public synchronized Connection getConnection(String schema) throws SQLException
    {
        List<Connection> connections = connectionsMap.computeIfAbsent(schema, (s) -> new ArrayList<>());
        for(Iterator it = connections.iterator(); it.hasNext(); )
        {
            Connection c = (Connection)it.next();

            if(c.isClosed())
            {
                it.remove();
            }
            else if(!c.isBusy())
            {
                return c;
            }
        }
        if(connections.size() == max)
        {
            logger.warn("Connection pool: max number of connections reached! ");

            // return a busy connection...
            return connections.get(0);
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

        // schema TODO
//        if(schema != null && schema.length() > 0)
//        {
//            String schemaQuery = vendor.getSchemaQuery();
//
//            if(schemaQuery != null)
//            {
//                schemaQuery = schemaQuery.replace("$schema", schema);
//                Statement stmt = connection.createStatement();
//                stmt.executeUpdate(schemaQuery);
//                stmt.close();
//            }
//            else
//            {
//                connection.setSchema(schema);
//            }
//        }

        Connection connection = connectionFactory.newConnection();
        connection.setAutoCommit(autocommit);
        return connection;
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
