package com.republicate.skorm.jdbc;

import kotlinx.datetime.ConvertersKt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClassMapper {

    @FunctionalInterface
    private interface Converter
    {
        Object convert(Object o);
    }

    private static final Map<Class<?>, Converter> readMap;
    private static final Map<Class<?>, Converter> writeMap;
    static
    {
        readMap = new HashMap<>();
        readMap.put(java.sql.Date.class, obj -> new kotlinx.datetime.LocalDate(((java.sql.Date)obj).toLocalDate()));
        readMap.put(java.sql.Timestamp.class, obj -> new kotlinx.datetime.LocalDateTime(((java.sql.Timestamp)obj).toLocalDateTime()));

        writeMap = new HashMap<>();
        writeMap.put(kotlinx.datetime.LocalDate.class, obj -> java.sql.Date.valueOf(ConvertersKt.toJavaLocalDate((kotlinx.datetime.LocalDate)obj)));
        writeMap.put(kotlinx.datetime.LocalDateTime.class, obj -> java.sql.Timestamp.valueOf(ConvertersKt.toJavaLocalDateTime((kotlinx.datetime.LocalDateTime)obj)));
    }

    public static Object read(Object obj) {
        if (obj == null) return null;
        else return Optional.ofNullable(readMap.get(obj.getClass())).map(converter -> converter.convert(obj)).orElse(obj);
    }

    public static Object write(Object obj)
    {
        if (obj == null) return null;
        else return Optional.ofNullable(writeMap.get(obj.getClass())).map(converter -> converter.convert(obj)).orElse(obj);
    }
}
