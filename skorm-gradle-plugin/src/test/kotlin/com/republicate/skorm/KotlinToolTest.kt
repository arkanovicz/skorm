package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
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
}
