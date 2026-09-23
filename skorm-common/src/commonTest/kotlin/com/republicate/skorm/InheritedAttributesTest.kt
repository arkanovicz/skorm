package com.republicate.skorm

import com.republicate.kson.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A subtype's rows answer to the parent entity's attributes, while REST paths stay on the schema. */
class InheritedAttributesTest {

    private val noProcessor = object : Processor {
        override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? = null
        override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Json.Object? = null
        override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Sequence<Json.Object> = emptySequence()
        override suspend fun perform(path: String, params: Map<String, Any?>): Long = 0
        override suspend fun begin(schema: String): Transaction = throw UnsupportedOperationException()
        override val restMode = false
        override val config = Configuration()
        override fun close() {}
    }

    private class TestDatabase(processor: Processor) : Database("d", processor)
    private class TestSchema(db: Database) : Schema("s", db)
    private class TestEntity(name: String, schema: Schema, parent: Entity? = null) : Entity(name, schema, parent)

    @Test
    fun lookupWalksTheParentEntityBeforeTheSchema() {
        val db = TestDatabase(noProcessor)
        val schema = TestSchema(db)
        val person = TestEntity("person", schema)
        val vip = TestEntity("vip", schema, person)
        val onPerson = person.instanceAttributes.scalarAttribute<Int>("age", setOf("person_id"))
        val onSchema = schema.scalarAttribute<Int>("count", emptySet())

        assertSame(onPerson, vip.instanceAttributes.findAttribute<Int>("age"))
        assertSame(onSchema, vip.instanceAttributes.findAttribute<Int>("count"))
        // an own attribute shadows the inherited one
        val own = vip.instanceAttributes.scalarAttribute<Int>("age", setOf("person_id"))
        assertSame(own, vip.instanceAttributes.findAttribute<Int>("age"))
    }

    @Test
    fun anInheritedAttributeExecutesAtItsOwnersPath() {
        val db = TestDatabase(noProcessor)
        val schema = TestSchema(db)
        val person = TestEntity("person", schema)
        val vip = TestEntity("vip", schema, person)
        person.instanceAttributes.scalarAttribute<Int>("age", emptySet())
        val found = vip.instanceAttributes.findAttribute<Int>("age")
        assertEquals("/d/s/person/age", vip.instanceAttributes.prepare(found).first)
    }

    @Test
    fun pathsIgnoreTheParentEntity() {
        val db = TestDatabase(noProcessor)
        val schema = TestSchema(db)
        val person = TestEntity("person", schema)
        val vip = TestEntity("vip", schema, person)
        assertEquals("/d/s/vip", vip.instanceAttributes.path)
        assertEquals("/d/s/person", person.instanceAttributes.path)
    }

    @Test
    fun anEntityWithoutKindBuildsItsOwnRows() {
        val db = TestDatabase(noProcessor)
        val person = TestEntity("person", TestSchema(db))
        assertSame(person, person.new("anything").entity)
    }
}
