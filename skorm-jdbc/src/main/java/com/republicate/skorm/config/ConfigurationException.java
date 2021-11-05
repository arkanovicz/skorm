package com.republicate.skorm.config;

import java.io.IOException;

public class ConfigurationException extends RuntimeException
{
    private static final long serialVersionUID = 3686267961051930733L;

    public ConfigurationException(String exceptionMessage)
    {
        super(exceptionMessage);
    }

    public ConfigurationException(String exceptionMessage, Throwable wrapped)
    {
        super(exceptionMessage, wrapped);
    }

    public ConfigurationException(Throwable wrapped)
    {
        super(wrapped);
    }
}
