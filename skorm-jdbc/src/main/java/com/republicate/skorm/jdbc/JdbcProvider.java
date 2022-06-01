package com.republicate.skorm.jdbc;

import com.republicate.skorm.*;
import kotlin.jvm.Throws;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.SQLException;

public class JdbcProvider implements ConnectorFactory, Configurable {

    private final Configuration config = new Configuration();

    @NotNull
    @Override
    public Configuration getConfig() {
        return config;
    }

    @Override
    public String getConfigTag() {
        return "jdbc";
    }

    public String getUrl() {
        return config.getString("url");
    }

    public String getLogin() {
        return config.getString("login");
    }

    public String getPassword() {
        return config.getString("password");
    }

    public String getDriver() {
        return config.getString("driver");
    }

    private DataSource dataSource = null;

    public JdbcProvider()
    {
    }

    @NotNull
    @Override
    public Connector connect() throws SkormException {
        try
        {
            if (dataSource == null) initialize();
            java.sql.Connection connection =
                    getLogin() == null
                            ? dataSource.getConnection()
                            : dataSource.getConnection(getLogin(), getPassword());
            return new JdbcConnector(connection);
        }
        catch (SQLException sqle)
        {
            throw new SkormException("could not connect", sqle);
        }
    }

    @Override
    public synchronized void initialize() throws SkormException
    {
        if (dataSource == null)
        {
            if (getDriver() != null) {
                try
                {
                    Class.forName(getDriver());
                }
                catch (ClassNotFoundException cnfe)
                {
                    throw new SkormException("could not load driver " + getDriver(), cnfe);
                }
            }
            dataSource = new BasicDataSource(getUrl());
        }
    }
}
