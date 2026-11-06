package com.republicate.skorm.jdbc;
import com.republicate.kson.Json;
import kotlinx.datetime.ConvertersKt;
import kotlin.time.jdk8.InstantConversionsJDK8Kt;
import kotlin.uuid.Uuid;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
        readMap.put(java.time.LocalDate.class, obj -> new kotlinx.datetime.LocalDate((java.time.LocalDate)obj));
        readMap.put(java.time.LocalTime.class, obj -> new kotlinx.datetime.LocalTime((java.time.LocalTime)obj));
        readMap.put(java.time.LocalDateTime.class, obj -> new kotlinx.datetime.LocalDateTime((java.time.LocalDateTime)obj));
        readMap.put(OffsetDateTime.class, obj -> InstantConversionsJDK8Kt.toKotlinInstant(((OffsetDateTime)obj).toInstant()));
        // java.sql values go through the JVM's zone (a wall clock in a DST gap shifts): only array elements still arrive as such
        readMap.put(java.sql.Date.class, obj -> new kotlinx.datetime.LocalDate(((java.sql.Date)obj).toLocalDate()));
        readMap.put(java.sql.Time.class, obj -> new kotlinx.datetime.LocalTime(((java.sql.Time)obj).toLocalTime()));
        readMap.put(java.sql.Timestamp.class, obj -> new kotlinx.datetime.LocalDateTime(((java.sql.Timestamp)obj).toLocalDateTime()));
        readMap.put(UUID.class, obj -> Uuid.Companion.parse(obj.toString()));

        writeMap = new HashMap<>();
        // java.time, not java.sql: a wall clock must not pass through the JVM's zone
        writeMap.put(kotlinx.datetime.LocalDate.class, obj -> ConvertersKt.toJavaLocalDate((kotlinx.datetime.LocalDate)obj));
        writeMap.put(kotlinx.datetime.LocalTime.class, obj -> ConvertersKt.toJavaLocalTime((kotlinx.datetime.LocalTime)obj));
        writeMap.put(kotlinx.datetime.LocalDateTime.class, obj -> ConvertersKt.toJavaLocalDateTime((kotlinx.datetime.LocalDateTime)obj));
        // PostgreSQL's driver refuses java.time.Instant; every driver takes an OffsetDateTime
        writeMap.put(kotlin.time.Instant.class, obj -> OffsetDateTime.ofInstant(InstantConversionsJDK8Kt.toJavaInstant((kotlin.time.Instant)obj), ZoneOffset.UTC));
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
        if (obj instanceof Json) return obj.toString(); // JSON text; a pedantic vendor binds it untyped and the json/jsonb column types it
        return Optional.ofNullable(writeMap.get(obj.getClass())).map(converter -> converter.convert(obj)).orElse(obj);
    }

    /** a value that binds as a SQL array: a Collection (a Kotlin List) or a Java array, bytes excepted (a blob) */
    public static boolean isArray(Object obj)
    {
        return obj instanceof Collection || obj != null && obj.getClass().isArray() && !(obj instanceof byte[]);
    }

    /** the elements of such a value, each converted like a parameter */
    public static Object[] elements(Object obj)
    {
        Collection<?> items;
        if (obj instanceof Collection) items = (Collection<?>)obj;
        else
        {
            int n = java.lang.reflect.Array.getLength(obj);
            List<Object> boxed = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) boxed.add(java.lang.reflect.Array.get(obj, i));
            items = boxed;
        }
        Object[] ret = new Object[items.size()];
        int i = 0;
        for (Object item : items) ret[i++] = write(item);
        return ret;
    }

    private static final Map<Class<?>, String> sqlTypeNames = Map.ofEntries(
        Map.entry(Short.class, "smallint"),
        Map.entry(Integer.class, "integer"),
        Map.entry(Long.class, "bigint"),
        Map.entry(Float.class, "real"),
        Map.entry(Double.class, "double precision"),
        Map.entry(java.math.BigDecimal.class, "numeric"),
        Map.entry(Boolean.class, "boolean"),
        Map.entry(String.class, "varchar"),
        Map.entry(java.time.LocalDate.class, "date"),
        Map.entry(java.time.LocalTime.class, "time"),
        Map.entry(java.time.LocalDateTime.class, "timestamp"),
        Map.entry(OffsetDateTime.class, "timestamp with time zone"),
        Map.entry(UUID.class, "uuid"));

    /** the SQL element type Connection.createArrayOf wants, from the first element present (varchar when none) */
    public static String arrayTypeName(Object[] elements)
    {
        for (Object element : elements)
        {
            if (element != null) return sqlTypeNames.getOrDefault(element.getClass(), "varchar");
        }
        return "varchar";
    }

    /** PostgreSQL's array literal, every element quoted: bound untyped, the column types it (an enum included) */
    public static String arrayLiteral(Object[] elements)
    {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < elements.length; ++i)
        {
            if (i > 0) sb.append(',');
            if (elements[i] == null) sb.append("NULL");
            else sb.append('"').append(elements[i].toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }
}
