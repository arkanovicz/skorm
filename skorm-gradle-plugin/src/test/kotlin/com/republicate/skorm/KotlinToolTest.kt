package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Tests for edge cases in KotlinTool that could cause NPE or unclear errors
 * when entities lack primary keys or foreign keys are misconfigured.
 */
class KotlinToolTest {

    private val tool = KotlinTool()

    /**
     * Test that foreignKeyForwardQuery throws a clear error when the target table has no primary key.
     */
    @Test
    fun `foreignKeyForwardQuery fails clearly when target has no primary key`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create source table with PK
        val sourceTable = ASTTable(schema, "source")
        sourceTable.fields["id"] = com.republicate.kddl.ASTField(sourceTable, "id", "serial", true, true, true)
        sourceTable.fields["target_id"] = com.republicate.kddl.ASTField(sourceTable, "target_id", "integer", false, true, false)
        schema.tables[sourceTable.name] = sourceTable

        // Create target table WITHOUT PK
        val targetTable = ASTTable(schema, "target")
        targetTable.fields["name"] = com.republicate.kddl.ASTField(targetTable, "name", "varchar(100)", false, true, false)
        schema.tables[targetTable.name] = targetTable

        // Create FK from source to target (target has no PK)
        val fkField = sourceTable.fields["target_id"]!!
        val fk = ASTForeignKey(sourceTable, setOf(fkField), targetTable, true, false, false)

        val ex = assertThrows<IllegalStateException> {
            tool.foreignKeyForwardQuery(fk)
        }
        assertTrue(ex.message!!.contains("target"))
        assertTrue(ex.message!!.contains("no primary key"))
    }

    /**
     * Test that foreignKeyReverseQuery throws a clear error when the target table has no primary key.
     */
    @Test
    fun `foreignKeyReverseQuery fails clearly when target has no primary key`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create source table with a field for FK
        val sourceTable = ASTTable(schema, "source")
        sourceTable.fields["target_id"] = com.republicate.kddl.ASTField(sourceTable, "target_id", "integer", false, true, false)
        schema.tables[sourceTable.name] = sourceTable

        // Create target table WITHOUT PK
        val targetTable = ASTTable(schema, "target")
        targetTable.fields["name"] = com.republicate.kddl.ASTField(targetTable, "name", "varchar(100)", false, true, false)
        schema.tables[targetTable.name] = targetTable

        val fkField = sourceTable.fields["target_id"]!!
        val fk = ASTForeignKey(sourceTable, setOf(fkField), targetTable, true, false, false)

        val ex = assertThrows<IllegalStateException> {
            tool.foreignKeyReverseQuery(fk)
        }
        assertTrue(ex.message!!.contains("target"))
        assertTrue(ex.message!!.contains("no primary key"))
    }

    /**
     * Test that foreignKeyForwardQuery throws a clear error when FK column count doesn't match PK.
     */
    @Test
    fun `foreignKeyForwardQuery fails clearly when FK column count mismatches PK`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create source table
        val sourceTable = ASTTable(schema, "source")
        sourceTable.fields["target_id1"] = com.republicate.kddl.ASTField(sourceTable, "target_id1", "integer", false, true, false)
        sourceTable.fields["target_id2"] = com.republicate.kddl.ASTField(sourceTable, "target_id2", "integer", false, true, false)
        schema.tables[sourceTable.name] = sourceTable

        // Create target table with single-column PK
        val targetTable = ASTTable(schema, "target")
        targetTable.fields["id"] = com.republicate.kddl.ASTField(targetTable, "id", "serial", true, true, true)
        schema.tables[targetTable.name] = targetTable

        // Create FK with 2 columns pointing to 1-column PK
        val fk = ASTForeignKey(
            sourceTable,
            setOf(sourceTable.fields["target_id1"]!!, sourceTable.fields["target_id2"]!!),
            targetTable, true, false, false
        )

        val ex = assertThrows<IllegalStateException> {
            tool.foreignKeyForwardQuery(fk)
        }
        assertTrue(ex.message!!.contains("column count"))
    }

    /**
     * Test that joinTableQuery throws a clear error when join table has no foreign keys.
     */
    @Test
    fun `joinTableQuery fails clearly when join table has no foreign keys`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create a table with no FKs
        val joinTable = ASTTable(schema, "empty_join")
        joinTable.fields["id"] = com.republicate.kddl.ASTField(joinTable, "id", "serial", true, true, true)
        schema.tables[joinTable.name] = joinTable

        val ex = assertThrows<IllegalStateException> {
            tool.joinTableQuery(joinTable)
        }
        assertTrue(ex.message!!.contains("empty_join"))
        assertTrue(ex.message!!.contains("no foreign keys"))
    }

    /**
     * Test that joinTableQuery throws a clear error when join table has only one foreign key.
     */
    @Test
    fun `joinTableQuery fails clearly when join table has only one foreign key`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create target table
        val targetTable = ASTTable(schema, "target")
        targetTable.fields["id"] = com.republicate.kddl.ASTField(targetTable, "id", "serial", true, true, true)
        schema.tables[targetTable.name] = targetTable

        // Create join table with only ONE FK
        val joinTable = ASTTable(schema, "incomplete_join")
        joinTable.fields["target_id"] = com.republicate.kddl.ASTField(joinTable, "target_id", "integer", true, true, false)
        schema.tables[joinTable.name] = joinTable

        val fk = ASTForeignKey(joinTable, setOf(joinTable.fields["target_id"]!!), targetTable, true, false, false)
        joinTable.foreignKeys.add(fk)

        val ex = assertThrows<IllegalStateException> {
            tool.joinTableQuery(joinTable)
        }
        assertTrue(ex.message!!.contains("incomplete_join"))
        assertTrue(ex.message!!.contains("at least 2 foreign keys"))
    }

    /**
     * Test that joinTableQuery throws a clear error when a join target table has no primary key.
     */
    @Test
    fun `joinTableQuery fails clearly when join target has no primary key`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create two target tables - one with PK, one without
        val targetWithPk = ASTTable(schema, "target_with_pk")
        targetWithPk.fields["id"] = com.republicate.kddl.ASTField(targetWithPk, "id", "serial", true, true, true)
        schema.tables[targetWithPk.name] = targetWithPk

        val targetWithoutPk = ASTTable(schema, "target_without_pk")
        targetWithoutPk.fields["name"] = com.republicate.kddl.ASTField(targetWithoutPk, "name", "varchar(100)", false, true, false)
        schema.tables[targetWithoutPk.name] = targetWithoutPk

        // Create join table
        val joinTable = ASTTable(schema, "join_table")
        joinTable.fields["with_pk_id"] = com.republicate.kddl.ASTField(joinTable, "with_pk_id", "integer", true, true, false)
        joinTable.fields["without_pk_id"] = com.republicate.kddl.ASTField(joinTable, "without_pk_id", "integer", true, true, false)
        schema.tables[joinTable.name] = joinTable

        val fk1 = ASTForeignKey(joinTable, setOf(joinTable.fields["with_pk_id"]!!), targetWithPk, true, false, false)
        val fk2 = ASTForeignKey(joinTable, setOf(joinTable.fields["without_pk_id"]!!), targetWithoutPk, true, false, false)
        joinTable.foreignKeys.add(fk1)
        joinTable.foreignKeys.add(fk2)

        val ex = assertThrows<IllegalStateException> {
            tool.joinTableQuery(joinTable)
        }
        assertTrue(ex.message!!.contains("no primary key"))
    }

    /**
     * Test that a valid FK query generates correctly.
     */
    @Test
    fun `foreignKeyForwardQuery works with valid FK`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        // Create target table with PK
        val targetTable = ASTTable(schema, "author")
        targetTable.fields["author_id"] = com.republicate.kddl.ASTField(targetTable, "author_id", "serial", true, true, true)
        targetTable.fields["name"] = com.republicate.kddl.ASTField(targetTable, "name", "varchar(100)", false, true, false)
        schema.tables[targetTable.name] = targetTable

        // Create source table
        val sourceTable = ASTTable(schema, "book")
        sourceTable.fields["book_id"] = com.republicate.kddl.ASTField(sourceTable, "book_id", "serial", true, true, true)
        sourceTable.fields["author_id"] = com.republicate.kddl.ASTField(sourceTable, "author_id", "integer", false, true, false)
        schema.tables[sourceTable.name] = sourceTable

        val fk = ASTForeignKey(sourceTable, setOf(sourceTable.fields["author_id"]!!), targetTable, true, false, false)

        val query = tool.foreignKeyForwardQuery(fk)
        assertTrue(query.contains("SELECT * FROM test_schema.author"))
        assertTrue(query.contains("author.author_id = {author_id}"))
    }

    // ==================== Enum Tests ====================

    /**
     * Test isEnum detects enum types correctly.
     */
    @Test
    fun `isEnum returns true for enum types`() {
        assertTrue(tool.isEnum("enum('a','b')"))
        assertTrue(tool.isEnum("enum('human','bot')"))
        assertTrue(tool.isEnum("enum('pending','active','completed')"))
    }

    @Test
    fun `isEnum returns false for non-enum types`() {
        assertFalse(tool.isEnum("varchar"))
        assertFalse(tool.isEnum("integer"))
        assertFalse(tool.isEnum("boolean"))
        assertFalse(tool.isEnum("serial"))
    }

    /**
     * Test enumValues extracts enum values correctly.
     */
    @Test
    fun `enumValues extracts values from enum field`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "status", "enum('pending','active','completed')", false, true, false)

        val values = tool.enumValues(field)
        assertEquals(listOf("pending", "active", "completed"), values)
    }

    @Test
    fun `enumValues handles two-value enum`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "mode", "enum('human','bot')", false, true, false)

        val values = tool.enumValues(field)
        assertEquals(listOf("human", "bot"), values)
    }

    @Test
    fun `enumValues handles single-value enum`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "single", "enum('only')", false, true, false)

        val values = tool.enumValues(field)
        assertEquals(listOf("only"), values)
    }

    /**
     * Test enumName returns alias when present.
     */
    @Test
    fun `enumName returns alias when present`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "mode", "enum('human','bot')", false, true, false, false, null, "GameMode")

        assertEquals("GameMode", tool.enumName(field))
    }

    @Test
    fun `enumName returns pascal-cased field name when no alias`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "game_status", "enum('a','b')", false, true, false, false, null, null)

        assertEquals("GameStatus", tool.enumName(field))
    }

    @Test
    fun `enumName handles simple field name without alias`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "status", "enum('a','b')", false, true, false)

        assertEquals("Status", tool.enumName(field))
    }

    /**
     * Test type() returns correct enum type name.
     */
    @Test
    fun `type returns enum alias when present`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "mode", "enum('human','bot')", false, true, false, false, null, "GameMode")

        assertEquals("GameMode", tool.type(field))
    }

    @Test
    fun `type returns pascal-cased name for enum without alias`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        val field = ASTField(table, "status", "enum('a','b')", false, true, false)

        assertEquals("Status", tool.type(field))
    }

    @Test
    fun `type returns standard types correctly`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        val table = ASTTable(schema, "test_table")

        assertEquals("Int", tool.type(ASTField(table, "count", "integer", false, true, false)))
        assertEquals("String", tool.type(ASTField(table, "name", "varchar(50)", false, true, false)))
        assertEquals("Boolean", tool.type(ASTField(table, "active", "boolean", false, true, false)))
        assertEquals("Long", tool.type(ASTField(table, "big", "long", false, true, false)))
        assertEquals("Double", tool.type(ASTField(table, "amount", "double", false, true, false)))
    }

    /**
     * Test enums() collects all enum fields from schema.
     */
    @Test
    fun `enums collects all enum fields from schema`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        val table = ASTTable(schema, "game")
        table.fields["id"] = ASTField(table, "id", "serial", true, true, true)
        table.fields["mode"] = ASTField(table, "mode", "enum('human','bot')", false, true, false, false, null, "GameMode")
        table.fields["status"] = ASTField(table, "status", "enum('waiting','playing','finished')", false, true, false)
        table.fields["name"] = ASTField(table, "name", "varchar(50)", false, true, false)
        schema.tables[table.name] = table

        val enums = tool.enums(schema)

        assertEquals(2, enums.size)
        assertTrue(enums.any { it.name == "mode" })
        assertTrue(enums.any { it.name == "status" })
        assertFalse(enums.any { it.name == "id" })
        assertFalse(enums.any { it.name == "name" })
    }

    @Test
    fun `enums collects from multiple tables`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        val table1 = ASTTable(schema, "game")
        table1.fields["status"] = ASTField(table1, "status", "enum('a','b')", false, true, false)
        schema.tables[table1.name] = table1

        val table2 = ASTTable(schema, "player")
        table2.fields["role"] = ASTField(table2, "role", "enum('admin','user')", false, true, false, false, null, "UserRole")
        schema.tables[table2.name] = table2

        val enums = tool.enums(schema)

        assertEquals(2, enums.size)
        assertTrue(enums.any { it.name == "status" })
        assertTrue(enums.any { it.name == "role" && it.alias == "UserRole" })
    }

    @Test
    fun `enums returns empty list when no enums in schema`() {
        val db = ASTDatabase("test_db")
        val schema = ASTSchema(db, "test_schema")
        db.schemas[schema.name] = schema

        val table = ASTTable(schema, "simple")
        table.fields["id"] = ASTField(table, "id", "serial", true, true, true)
        table.fields["name"] = ASTField(table, "name", "varchar(50)", false, true, false)
        schema.tables[table.name] = table

        val enums = tool.enums(schema)
        assertTrue(enums.isEmpty())
    }
}
