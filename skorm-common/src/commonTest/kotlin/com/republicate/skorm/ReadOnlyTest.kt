package com.republicate.skorm

import com.republicate.kson.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The read-only objects refuse what only their mutable twins do. */
class ReadOnlyTest {

    private class Recorder : Processor {
        var performed = mutableListOf<String>()
        override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? = null
        override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Json.Object? = null
        override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Sequence<Json.Object> = emptySequence()
        override suspend fun perform(path: String, params: Map<String, Any?>): Long { performed += path; return 1 }
        override suspend fun begin(schema: String): Transaction = throw UnsupportedOperationException()
        override val restMode = false
        override val config = Configuration()
        override fun close() {}
    }

    private class Db(processor: Processor) : Database("d", processor)
    private class MutableDb(processor: Processor) : Database("d", processor), MutableDatabase
    private class Sch(db: Database) : Schema("s", db)
    private class MutableSch(db: Database) : Schema("s", db), MutableSchema
    private class Ent(schema: Schema) : Entity("book", schema)
    private class MutableEnt(schema: Schema) : Entity("book", schema), MutableEntity {
        override fun new() = Row(this)
    }
    private class Row(entity: Entity) : Instance(entity), MutableInstance

    private fun Entity.withTitle() = apply { addField(Field("title", "varchar")) }

    @Test
    fun aReadOnlyRowRefusesWrites() {
        val row = Ent(Sch(Db(Recorder()))).withTitle().new()
        assertFailsWith<SkormException> { row.put("title", "x") }
        assertFailsWith<SkormException> { row.putAll(mapOf("title" to "x")) }
        assertFailsWith<SkormException> { row.remove("title") }
        assertFailsWith<SkormException> { row.clear() }
        assertTrue(row.isEmpty())
    }

    @Test
    fun aMutableRowWrites() {
        val row = MutableEnt(MutableSch(MutableDb(Recorder()))).withTitle().new()
        row.put("title", "x")
        assertEquals("x", row["title"])
        assertTrue(row.isDirty())
        assertFailsWith<SkormException> { row.put("nope", 1) }
    }

    @Test
    fun aReadOnlyHolderRegistersNoMutationAndPerformsNone() {
        val recorder = Recorder()
        val schema = Sch(Db(recorder))
        assertFailsWith<SkormException> { schema.addAttribute(MutationAttribute("wipe")) }
        val entity = Ent(schema)
        assertFailsWith<SkormException> { entity.instanceAttributes.addAttribute(MutationAttribute("wipe")) }
        // a mutable row of a read-only entity cannot exist, but a mutation reaching the holder is still refused
        runTest {
            assertFailsWith<SkormException> { entity.instanceAttributes.mutate(MutationAttribute("wipe")) }
        }
        assertTrue(recorder.performed.isEmpty())
    }

    @Test
    fun aMutableHolderPerforms() = runTest {
        val recorder = Recorder()
        val schema = MutableSch(MutableDb(recorder))
        schema.addAttribute(MutationAttribute("wipe"))
        assertEquals(1, schema.perform("wipe"))
        assertEquals(listOf("/d/s/wipe"), recorder.performed)
    }
}
