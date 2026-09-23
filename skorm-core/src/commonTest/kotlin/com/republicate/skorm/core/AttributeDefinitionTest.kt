package com.republicate.skorm.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AttributeDefinitionTest {

    private fun statements(sql: String) = AttributeDefinition.parse(sql).let { it.queries(it.parameters()) }

    @Test
    fun aSemicolonBetweenParenthesesEndsNoStatement() {
        // the shape of a PostgreSQL rule with several actions, nested parentheses included
        val rule = """
            CREATE RULE insert_vip AS ON INSERT TO vip DO INSTEAD (
              INSERT INTO person (person_id, name) VALUES (COALESCE(NEW.person_id, NEXTVAL('seq')), NEW.name);
              INSERT INTO base_vip (person_id) VALUES (CURRVAL('seq'));
            );
            CREATE TABLE after_it (id int);
        """.trimIndent()
        val parsed = statements(rule)
        assertEquals(2, parsed.size)
        assertEquals("CREATE RULE", parsed[0].stmt.trim().take(11))
        assertEquals("CREATE TABLE after_it (id int);", parsed[1].stmt.trim())
    }

    @Test
    fun parametersAndQuotesInsideParenthesesStillCount() {
        val parsed = statements("INSERT INTO t (a, b) VALUES ({a}, 'x;y'); DELETE FROM t WHERE a = {a};")
        assertEquals(2, parsed.size)
        assertEquals(listOf("a"), parsed[0].params)
        assertEquals("INSERT INTO t (a, b) VALUES (?, 'x;y');", parsed[0].stmt.trim())
        assertEquals(listOf("a"), parsed[1].params)
    }
}
