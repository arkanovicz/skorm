package com.republicate.skorm;


import kotlin.sequences.Sequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JdbcConnector implements Connector
{
    @NotNull
    @Override
    public Sequence<Row> query(@NotNull String query, @Nullable Object... params)
    {
        return null;
    }

    @Override
    public long mutate(@NotNull String query, @Nullable Object... params)
    {
        return 0;
    }

    @Override
    public void begin()
    {

    }

    @Override
    public void savePoint(@NotNull String name)
    {

    }

    @Override
    public void rollback(@Nullable String savePoint)
    {

    }

    @Override
    public void commit()
    {

    }
}
