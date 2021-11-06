package com.republicate.skorm.jdbc;

import javax.sql.DataSource;
import java.sql.SQLException;

public class ConnectionFactory
{
    public ConnectionFactory(DataSource datasource)
    {
        this(datasource, null, null);
    }

    public ConnectionFactory(DataSource datasource, String user, String password)
    {
        this.dataSource = datasource;
        this.user = user;
        this.password = password;
    }

    public boolean hasCredentials()
    {
        return user != null && password != null;
    }

    public Connection newConnection() throws SQLException
    {
        if (hasCredentials())
        {
            return new Connection(dataSource.getConnection(user, password));
        }
        else
        {
            return new Connection(dataSource.getConnection());
        }
    }

    private DataSource dataSource;
    private String user;
    private String password;
}
