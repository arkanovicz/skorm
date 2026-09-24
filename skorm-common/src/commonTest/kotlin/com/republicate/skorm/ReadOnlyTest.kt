package com.republicate.skorm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
        override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Row? = null
        override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Flow<Row> = emptyFlow()
        override suspend fun perform(path: String, params: Map<String, Any?>): Long { performed += path; return 1 }
        override suspend fun begin(schema: String): Transaction = throw UnsupportedOperationException()
        override val restMode = false
        override val config = Configuration()
        override fun close() {}
    }

    private class Db(processor: Processor) : Database("d", processor)
    private class MutableDb(processor: Processor) : Database("d", processor), MutableDatabase {
        override val readOnly: Database by lazy { Db(processor) }
    }
    private class Sch(db: Database) : Schema("s", db)
    private class MutableSch(db: Database) : Schema("s", db), MutableSchema
    private class Ent(schema: Schema) : Entity("book", schema)
    private class MutableEnt(schema: Schema) : Entity("book", schema), MutableEntity {
        override fun new() = Row(this)
    }
    private class Row(entity: Entity) : MutableInstanceImpl(entity)

    private fun <E: Entity> E.withTitle() = apply { addField(Field("title", "varchar")) }

    @Test
    fun aReadOnlyRowHasNoWrites() {
        val row = Ent(Sch(Db(Recorder()))).withTitle().new()
        assertTrue(row !is MutableMap<*, *>, "a read-only row is not a mutable map")
        assertTrue(row !is MutableInstance)
        assertTrue(row is Json.Object && row !is Json.MutableObject)
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
    fun aReadOnlyDatabaseHoldsAProcessorThatCannotWrite() = runTest {
        val recorder = Recorder()
        val mutable = MutableDb(recorder)
        val readOnly = mutable.readOnly
        assertTrue(mutable.processor === recorder, "the mutable database holds the processor itself")
        assertTrue(readOnly.processor !== recorder && readOnly.processor.underlying === recorder)
        // what reflection reaches on a read-only database: the interface, writes refused
        assertFailsWith<SkormException> { readOnly.processor.perform("/d/s/wipe", mapOf()) }
        assertFailsWith<SkormException> { readOnly.processor.eval("/d/s/x", mapOf(), mutable = true) }
        assertEquals(null, readOnly.processor.eval("/d/s/x", mapOf()))
        assertTrue(recorder.performed.isEmpty())
        // a lone read-only database wraps too
        assertTrue(Db(Recorder()).processor is ReadOnlyProcessor)
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
