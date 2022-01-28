package com.republicate.skorm.jdbc;


import com.republicate.skorm.Connector;
import com.republicate.skorm.QueryResult;
import kotlin.sequences.Sequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

public class JdbcConnector implements Connector
{
    private final Connection connection;

    protected JdbcConnector(Connection connection)
    {
        this.connection = connection;
    }

    @NotNull
    @Override
    public QueryResult query(@NotNull String query, @Nullable Object... params)
    {
        return null;
    }

    @NotNull
    @Override
    public long mutate(@NotNull String query, @Nullable Object... params)
    {
        return 0;
    }
}
