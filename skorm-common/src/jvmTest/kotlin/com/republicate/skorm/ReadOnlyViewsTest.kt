package com.republicate.skorm

import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What reflection sees of the model is read-only too: the runtime class refuses what the Kotlin type hides. */
class ReadOnlyViewsTest {

    private val noProcessor = object : Processor {
        override suspend fun eval(path: String, params: Map<String, Any?>, mutable: Boolean): Any? = null
        override suspend fun retrieve(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Row? = null
        override suspend fun query(path: String, params: Map<String, Any?>, factory: RowFactory?, mutable: Boolean): Sequence<Row> = emptySequence()
        override suspend fun perform(path: String, params: Map<String, Any?>): Long = 0
        override suspend fun begin(schema: String): Transaction = throw UnsupportedOperationException()
        override val restMode = false
        override val config = Configuration()
        override fun close() {}
    }
    private class Db(processor: Processor) : Database("d", processor)
    private class Sch(db: Database) : Schema("s", db)
    private class Ent(schema: Schema) : Entity("book", schema)

    /** as Velocity calls it: through the Java interface, on whatever the runtime object is */
    private fun Any.javaCall(iface: String, method: String): Any? =
        try { Class.forName(iface).getMethod(method).invoke(this) } catch (e: InvocationTargetException) { throw e.cause!! }

    @Test
    fun theModelViewsRefuseMutation() {
        val db = Db(noProcessor)
        val schema = Sch(db)
        val entity = Ent(schema).apply { addField(Field("title", "varchar")) }
        schema.addAttribute(LongAttribute("count", emptySet()))

        assertFailsWith<UnsupportedOperationException> { entity.fields.javaCall("java.util.Map", "clear") }
        assertFailsWith<UnsupportedOperationException> { schema.attributes.javaCall("java.util.Map", "clear") }
        assertFailsWith<UnsupportedOperationException> { db.schemas.javaCall("java.util.Collection", "clear") }
        assertFailsWith<UnsupportedOperationException> { schema.entities.javaCall("java.util.Collection", "clear") }
        // nor through the views of the views
        assertFailsWith<UnsupportedOperationException> { entity.fields.keys.javaCall("java.util.Set", "clear") }
        assertFailsWith<UnsupportedOperationException> { entity.fields.values.javaCall("java.util.Collection", "clear") }
        assertFailsWith<UnsupportedOperationException> { entity.fields.entries.javaCall("java.util.Set", "clear") }
        assertFailsWith<UnsupportedOperationException> {
            db.schemas.iterator().apply { next() }.javaCall("java.util.Iterator", "remove")
        }

        assertEquals(listOf("title"), entity.fields.keys.toList())
        assertEquals(listOf(schema), db.schemas.toList())
        assertEquals(listOf<Entity>(entity), schema.entities.toList())
        assertEquals(setOf("count"), schema.attributes.keys)
    }

    @Test
    fun theViewsAreLive() {
        val entity = Ent(Sch(Db(noProcessor)))
        val fields = entity.fields
        entity.addField(Field("title", "varchar"))
        assertEquals(setOf("title"), fields.keys)
        assertEquals(entity.fields, fields.toMap())
    }
}
