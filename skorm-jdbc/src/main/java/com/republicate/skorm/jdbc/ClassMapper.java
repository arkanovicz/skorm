package com.republicate.skorm.jdbc;
import kotlinx.datetime.ConvertersKt;
import kotlin.uuid.Uuid;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        readMap.put(UUID.class, obj -> Uuid.Companion.parse(obj.toString()));

        writeMap = new HashMap<>();
        writeMap.put(kotlinx.datetime.LocalDate.class, obj -> java.sql.Date.valueOf(ConvertersKt.toJavaLocalDate((kotlinx.datetime.LocalDate)obj)));
        writeMap.put(kotlinx.datetime.LocalDateTime.class, obj -> java.sql.Timestamp.valueOf(ConvertersKt.toJavaLocalDateTime((kotlinx.datetime.LocalDateTime)obj)));
        writeMap.put(Uuid.class, obj -> UUID.fromString(obj.toString()));
    }

    public static Object read(Object obj) {
        if (obj == null) return null;
        // Handle java.sql.Array (interface - can't use map lookup)
        if (obj instanceof java.sql.Array) {
            return convertSqlArray((java.sql.Array) obj);
        }
        return Optional.ofNullable(readMap.get(obj.getClass())).map(converter -> converter.convert(obj)).orElse(obj);
    }

    private static List<?> convertSqlArray(java.sql.Array sqlArray) {
        try {
            Object arr = sqlArray.getArray();
            if (arr instanceof Object[]) {
                // Recursively convert elements (handles nested arrays, dates, etc.)
                Object[] objArr = (Object[]) arr;
                Object[] converted = new Object[objArr.length];
                for (int i = 0; i < objArr.length; i++) {
                    converted[i] = read(objArr[i]);
                }
                return Arrays.asList(converted);
            }
            // Primitive arrays - convert to List
            if (arr instanceof int[]) return Arrays.stream((int[]) arr).boxed().toList();
            if (arr instanceof long[]) return Arrays.stream((long[]) arr).boxed().toList();
            if (arr instanceof double[]) return Arrays.stream((double[]) arr).boxed().toList();
            // Fallback
            return List.of(arr);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to convert SQL array", e);
        }
    }

    public static Object write(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Enum) return obj.toString(); // bind enums (e.g. generated Kotlin enum params) as their constant name
        return Optional.ofNullable(writeMap.get(obj.getClass())).map(converter -> converter.convert(obj)).orElse(obj);
    }
}
