package com.republicate.skorm.jdbc;

import com.republicate.skorm.Connector;
import com.republicate.skorm.ConnectorFactory;
import com.republicate.skorm.SkormException;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.SQLException;

public class JdbcProvider implements ConnectorFactory {

    private final String url;
    private final String login;
    private final String password;
    private final String driver;
    private DataSource dataSource = null;

    public JdbcProvider(String url)
    {
        this(url, null, null);
    }

    public JdbcProvider(String url, String login, String password)
    {
        this(url, login, password, null);
    }

    public JdbcProvider(String url, String login, String password, String driver)
    {
        this.url = url;
        this.login = login;
        this.password = password;
        this.driver = driver;
    }

    @NotNull
    @Override
    public Connector connect() throws SkormException {
        try
        {
            if (dataSource == null) initialize();
            java.sql.Connection connection =
                    login == null
                            ? dataSource.getConnection()
                            : dataSource.getConnection(login, password);
            return new JdbcConnector(connection);
        }
        catch (SQLException sqle)
        {
            throw new SkormException("could not connect", sqle);
        }
    }

    private synchronized void initialize() throws SkormException
    {
        if (dataSource == null)
        {
            if (driver != null) {
                try
                {
                    Class.forName(driver);
                }
                catch (ClassNotFoundException cnfe)
                {
                    throw new SkormException("could not load driver " + driver, cnfe);
                }
            }
            dataSource = new BasicDataSource(url);
        }
    }
}
