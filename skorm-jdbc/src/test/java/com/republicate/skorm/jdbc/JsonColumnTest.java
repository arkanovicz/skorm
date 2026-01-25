package com.republicate.skorm.jdbc;

import com.republicate.kson.Json;
import com.republicate.skorm.QueryResult;
import com.republicate.skorm.core.CoreProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JSON columns are automatically parsed to Json objects/arrays.
 */
public class JsonColumnTest {

    private static final String URL = "jdbc:h2:mem:jsontest;DB_CLOSE_DELAY=-1";
    private JdbcConnector connector;
    private CoreProcessor processor;

    @BeforeEach
    public void setUp() throws Exception {
        connector = new JdbcConnector(URL, null, null, null, "PUBLIC");
        connector.initialize();
        processor = new CoreProcessor(connector);

        // Create test table with JSON column
        try (var conn = DriverManager.getConnection(URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS json_test");
            stmt.execute("CREATE TABLE json_test (id INT PRIMARY KEY, data JSON)");
            stmt.execute("INSERT INTO json_test VALUES (1, '{\"name\": \"Alice\", \"age\": 30}')");
            stmt.execute("INSERT INTO json_test VALUES (2, '[1, 2, 3]')");
            stmt.execute("INSERT INTO json_test VALUES (3, NULL)");
        }
    }

    @Test
    public void testJsonObjectColumn() throws Exception {
        QueryResult result = connector.query("PUBLIC", "SELECT id, data FROM json_test WHERE id = 1");

        // Verify types array contains JSON
        assertEquals(2, result.getTypes().length);
        assertEquals("INTEGER", result.getTypes()[0]);
        assertEquals("JSON", result.getTypes()[1]);

        // Verify data is parsed as Json.Object
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        assertEquals(1, row[0]);

        // The value should still be a string at this point (connector doesn't filter)
        // Filtering happens in CoreProcessor
        assertNotNull(row[1]);
    }

    @Test
    public void testJsonArrayColumn() throws Exception {
        QueryResult result = connector.query("PUBLIC", "SELECT data FROM json_test WHERE id = 2");

        assertEquals("JSON", result.getTypes()[0]);

        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        assertNotNull(row[0]);
    }

    @Test
    public void testNullJsonColumn() throws Exception {
        QueryResult result = connector.query("PUBLIC", "SELECT data FROM json_test WHERE id = 3");

        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        assertNull(row[0]);
    }

    @Test
    public void testComputedJsonColumn() throws Exception {
        // Test that computed JSON columns also get the correct type
        // H2's JSON_ARRAY function returns JSON type
        QueryResult result = connector.query("PUBLIC",
            "SELECT JSON_ARRAY(1, 2, 3) as arr, JSON_OBJECT('key': 'value') as obj");

        assertEquals(2, result.getTypes().length);
        assertEquals("JSON", result.getTypes()[0]);
        assertEquals("JSON", result.getTypes()[1]);

        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        assertNotNull(row[0]);
        assertNotNull(row[1]);
    }

    @Test
    public void testProcessorFiltersJsonObject() throws Exception {
        // Test that CoreProcessor's downstreamFilter parses JSON objects
        QueryResult result = connector.query("PUBLIC", "SELECT data FROM json_test WHERE id = 1");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        String type = result.getTypes()[0];

        // Apply the filter like CoreProcessor does
        Object filtered = processor.downstreamFilter(type, row[0]);

        // Should be parsed as Json.Object
        assertInstanceOf(Json.Object.class, filtered);
        Json.Object json = (Json.Object) filtered;
        assertEquals("Alice", json.getString("name"));
        assertEquals(30, json.getInt("age"));
    }

    @Test
    public void testProcessorFiltersJsonArray() throws Exception {
        // Test that CoreProcessor's downstreamFilter parses JSON arrays
        QueryResult result = connector.query("PUBLIC", "SELECT data FROM json_test WHERE id = 2");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        String type = result.getTypes()[0];

        // Apply the filter
        Object filtered = processor.downstreamFilter(type, row[0]);

        // Should be parsed as Json.Array
        assertInstanceOf(Json.Array.class, filtered);
        Json.Array json = (Json.Array) filtered;
        assertEquals(3, json.size());
        assertEquals(1, ((Number) json.get(0)).intValue());
    }

    @Test
    public void testProcessorFiltersNullJson() throws Exception {
        // Test that null JSON values stay null
        QueryResult result = connector.query("PUBLIC", "SELECT data FROM json_test WHERE id = 3");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();
        String type = result.getTypes()[0];

        Object filtered = processor.downstreamFilter(type, row[0]);
        assertNull(filtered);
    }

    // === SQL Array Tests ===

    @Test
    public void testIntegerArray() throws Exception {
        try (var conn = DriverManager.getConnection(URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS array_test");
            stmt.execute("CREATE TABLE array_test (id INT PRIMARY KEY, nums INT ARRAY)");
            stmt.execute("INSERT INTO array_test VALUES (1, ARRAY[10, 20, 30])");
        }

        QueryResult result = connector.query("PUBLIC", "SELECT nums FROM array_test WHERE id = 1");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();

        // ClassMapper should convert java.sql.Array to List
        assertInstanceOf(java.util.List.class, row[0]);
        @SuppressWarnings("unchecked")
        java.util.List<Integer> nums = (java.util.List<Integer>) row[0];
        assertEquals(3, nums.size());
        assertEquals(10, nums.get(0));
        assertEquals(20, nums.get(1));
        assertEquals(30, nums.get(2));
    }

    @Test
    public void testStringArray() throws Exception {
        try (var conn = DriverManager.getConnection(URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS array_test");
            stmt.execute("CREATE TABLE array_test (id INT PRIMARY KEY, names VARCHAR(100) ARRAY)");
            stmt.execute("INSERT INTO array_test VALUES (1, ARRAY['alice', 'bob', 'charlie'])");
        }

        QueryResult result = connector.query("PUBLIC", "SELECT names FROM array_test WHERE id = 1");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();

        assertInstanceOf(java.util.List.class, row[0]);
        @SuppressWarnings("unchecked")
        java.util.List<String> names = (java.util.List<String>) row[0];
        assertEquals(3, names.size());
        assertEquals("alice", names.get(0));
        assertEquals("bob", names.get(1));
        assertEquals("charlie", names.get(2));
    }

    @Test
    public void testEmptyArray() throws Exception {
        try (var conn = DriverManager.getConnection(URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS array_test");
            stmt.execute("CREATE TABLE array_test (id INT PRIMARY KEY, nums INT ARRAY)");
            stmt.execute("INSERT INTO array_test VALUES (1, ARRAY[])");
        }

        QueryResult result = connector.query("PUBLIC", "SELECT nums FROM array_test WHERE id = 1");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();

        assertInstanceOf(java.util.List.class, row[0]);
        @SuppressWarnings("unchecked")
        java.util.List<?> nums = (java.util.List<?>) row[0];
        assertTrue(nums.isEmpty());
    }

    @Test
    public void testNullArray() throws Exception {
        try (var conn = DriverManager.getConnection(URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("DROP TABLE IF EXISTS array_test");
            stmt.execute("CREATE TABLE array_test (id INT PRIMARY KEY, nums INT ARRAY)");
            stmt.execute("INSERT INTO array_test VALUES (1, NULL)");
        }

        QueryResult result = connector.query("PUBLIC", "SELECT nums FROM array_test WHERE id = 1");
        assertTrue(result.getValues().hasNext());
        Object[] row = result.getValues().next();

        assertNull(row[0]);
    }
}
