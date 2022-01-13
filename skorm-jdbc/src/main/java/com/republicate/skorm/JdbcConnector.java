package com.republicate.skorm;


import kotlin.sequences.Sequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class JdbcConnector implements Connector
{
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
