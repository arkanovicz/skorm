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


import com.republicate.skorm.GeneratedKeyMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * this class encapsulates a jdbc PreparedStatement (and a potential ResultSet encapsulated by its base class).
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class PooledStatement // TODO implements RowValues
{
    protected static Logger logger = LoggerFactory.getLogger("jdbc");

    /**
     * time tag.
     */
    private long tagTime = 0;

    /**
     * valid statement?
     */
    private boolean valid = true;

    /**
     * is this object in use?
     */
    private boolean inUse = false;

    /**
     * database connection.
     */
    protected Connection connection = null;

    /**
     * wrapped prepared statement.
     */
    private transient PreparedStatement preparedStatement = null;

    /**
     * result set.
     */
    protected transient ResultSet resultSet = null;

    /**
     * build a new PooledStatement.
     *
     * @param connection database connection
     * @param preparedStatement wrapped prepared statement
     */
    public PooledStatement(Connection connection, PreparedStatement preparedStatement)
    {
        this.connection = connection;
        this.preparedStatement = preparedStatement;
    }

    /**
     * get the time tag of this pooled object.
     *
     * @return the time tag
     */
    public long getTagTime()
    {
        return tagTime;
    }

    /**
     * reset the time tag.
     */
    public void resetTagTime()
    {
        tagTime = System.currentTimeMillis();
    }

    /**
     * notify this object that it is in use.
     */
    public void notifyInUse()
    {
        inUse = true;
        resetTagTime();
    }

    /**
     * notify this object that it is no more in use.
     */
    public void notifyOver()
    {
        try
        {
            if(resultSet != null && !resultSet.isClosed())
            {
                resultSet.close();
            }
        }
        catch(SQLException sqle) {}    // ignore
        resultSet = null;
        inUse = false;
    }

    /**
     * check whether this pooled object is marked as valid or invalid.
     * (used in the recovery execute)
     *
     * @return whether this object is in use
     */
    public boolean isValid()
    {
        return preparedStatement != null && valid;
    }

    public synchronized ResultSet executeQuery(Object... paramValues) throws SQLException
    {
        try
        {
            setParamValues(paramValues);
            getConnection().enterBusyState();
            return resultSet = preparedStatement.executeQuery();
        }
        finally
        {
            getConnection().leaveBusyState();
        }
    }

    public synchronized long executeUpdate(Object... paramValues) throws SQLException
    {
        try
        {
            long count = 0;
            setParamValues(paramValues);
            getConnection().enterBusyState();
            if (!preparedStatement.execute())
            {
                count = preparedStatement.getUpdateCount();
            }
            return count;
        }
        finally
        {
            getConnection().leaveBusyState();
        }
    }


    private void setParamValues(Object[] paramValues) throws SQLException
    {
        if (logger.isTraceEnabled())
        {
            logger.trace("params-{}", Arrays.asList(paramValues));
        }
        for (int i = 0; i < paramValues.length; ++i)
        {
            Object value = paramValues[i];
            if (value instanceof GeneratedKeyMarker) break;
            preparedStatement.setObject(i + 1, ClassMapper.write(value));
        }
    }

    /**
     * issue the modification query of this prepared statement.
     *
     * @param params parameter values
     * @exception SQLException thrown by the database engine
     * @return the numer of affected rows
     */
    public synchronized int update(List params) throws SQLException
    {
        try
        {
            Object arrParams[] = new Object[params.size()];
            setParamValues((Object[])params.toArray(arrParams));
            connection.enterBusyState();

            int rows = preparedStatement.executeUpdate();

            return rows;
        }
        finally
        {
            connection.leaveBusyState();
            notifyOver();
        }
    }

    /**
     * get the object value of the specified resultset column.
     *
     * @param key the name of the resultset column
     * @exception SQLException thrown by the database engine
     * @return the object value returned by jdbc
     */
    // TODO
//    public synchronized Serializable get(Object key) throws SQLException
//    {
//        if(!(key instanceof String) || resultSet == null)
//        {
//            return null;
//        }
//
//        Serializable ret = (Serializable)resultSet.getObject((String)key);
//
//        return ret;
//    }
//
//    public Set<String> keySet() throws SQLException
//    {
//        if(resultSet == null) return new HashSet<String>();
//        return new HashSet<String>(SqlUtils.getColumnNames(resultSet));
//    }

    /**
     * get the last insert id.
     *
     * @exception SQLException thrown by the database engine
     * @return the last insert id
     */
    public synchronized long getLastInsertID(String keyColumn) throws SQLException
    {
        return connection.getLastInsertId(preparedStatement, keyColumn);
    }

    /**
     * close this statement.
     *
     * @exception SQLException thrown by the database engine
     */
    public synchronized void close() throws SQLException
    {
        if(preparedStatement != null)
        {
            preparedStatement.close();
        }
    }

    /**
     * get statement Connection.
     *
     *  @return the Connection object (usually a ConnectionWrapper object)
     */
    public Connection getConnection()
    {
        return connection;
    }

    /**
     * check whether this pooled object is in use.
     *
     * @return whether this object is in use
     */
    public boolean isInUse()
    {
        return inUse;
    }

    /**
     * definitely mark this statement as meant to be deleted.
     */
    public void setInvalid()
    {
        valid = false;
    }
}
