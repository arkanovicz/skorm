package com.republicate.skorm

import com.republicate.kson.Json
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A row: a read-only map of its fields, knowing its entity. The generated row types are interfaces
 * extending this one, so that a table hierarchy and the mutable twins can both be expressed by
 * inheritance; [InstanceImpl] and [MutableInstanceImpl] are the storage behind them. The typed
 * getters are kson's, declared here so a generated interface can read its fields.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
interface Instance : Map<String, Any?> {
    val entity: Entity
    val isPersisted: Boolean
    val generatedPrimaryKey: Boolean get() = entity.primaryKey.size == 1 && entity.primaryKey.first().isGenerated
    /** whether writes await the database; a read-only row never has any */
    fun isDirty(): Boolean
    /** the row matches the database: after loading, after a write went through */
    fun setClean()
    suspend fun refresh()

    // raw loading: database values under database names, filtered on the way in, nothing marked dirty
    @SkormInternalApi fun putRawFields(from: Map<out String, Any?>)
    @SkormInternalApi fun putRawValue(key: String, value: Any?): Any?
    @SkormInternalApi fun putRawField(field: Field, value: Any?)

    fun getString(key: String): String?
    fun getBoolean(key: String): Boolean?
    fun getChar(key: String): Char?
    fun getByte(key: String): Byte?
    fun getShort(key: String): Short?
    fun getInt(key: String): Int?
    fun getInteger(key: String): Int?
    fun getLong(key: String): Long?
    fun getBigInteger(key: String): BigInteger?
    fun getFloat(key: String): Float?
    fun getDouble(key: String): Double?
    fun getBigDecimal(key: String): BigDecimal?
    fun getInstant(key: String): Instant?
    fun getLocalDateTime(key: String): LocalDateTime?
    fun getLocalDate(key: String): LocalDate?
    fun getLocalTime(key: String): LocalTime?
    fun getArray(key: String): Json.Array?
    fun getObject(key: String): Json.Object?
    fun getJson(key: String): Json?
    fun getBytes(key: String): ByteArray?
    fun getUuid(key: String): Uuid?
}

suspend inline fun <reified T: Any?> Instance.eval(attrName: String, vararg params: Any?) = entity.eval<T>(attrName, this, *params)
suspend inline fun <reified T: Row?> Instance.retrieve(attrName: String, vararg params: Any?) = entity.retrieve<T>(attrName, this, *params)
suspend inline fun <reified T: Row> Instance.query(attrName: String, vararg params: Any?) = entity.query<T>(attrName, this, *params)

private fun Instance.rawFieldName(key: String) = entity.instanceAttributes.processor.downstreamMapping(key)
private fun Instance.rawFieldValue(field: Field, value: Any?) = entity.instanceAttributes.processor.downstreamFilter(field.type, value)

/** A read-only row: a kson object owning its storage, which only the raw loading writes. */
open class InstanceImpl private constructor(override val entity: Entity, private val storage: MutableMap<String, Any?>) : Json.Object(storage), Instance {
    constructor(entity: Entity) : this(entity, LinkedHashMap())

    override var isPersisted = false
        protected set

    override suspend fun refresh() {
        if (!isPersisted) throw SkormException("cannot refresh a volatile instance")
        val self = entity.fetch(this) ?: throw SkormException("cannot refresh instance, it doesn't exist")
        storage.putAll(self)
        setClean()
    }

    override fun setClean() { isPersisted = true }
    override fun isDirty() = false

    // raw loading: database values under database names, filtered on the way in

    @SkormInternalApi
    override fun putRawFields(from: Map<out String, Any?>) {
        from.entries.forEach {
            val fieldName = rawFieldName(it.key)
            entity.fields[fieldName]?.also { field ->
                putRawField(field, it.value)
            } ?: putRawValue(fieldName, it.value)   // non-entity columns (e.g. composite extra fields) kept under their mapped name
        }
    }

    // to allow subclasses to add key-value pairs besides entity columns
    @SkormInternalApi
    override fun putRawValue(key: String, value: Any?): Any? = storage.put(key, value)

    @SkormInternalApi
    override fun putRawField(field: Field, value: Any?) {
        storage[field.name] = rawFieldValue(field, value)
    }
}

/** A mutable row: a kson mutable object, whose typed writes track the dirty fields. */
open class MutableInstanceImpl(override val entity: Entity) : Json.MutableObject(), MutableInstance {
    val dirtyFields = BitSet(MAX_FIELDS) // init size needed for multiplatform
    override var isPersisted = false

    override suspend fun refresh() {
        if (!isPersisted) throw SkormException("cannot refresh a volatile instance")
        val self = entity.fetch(this) ?: throw SkormException("cannot refresh instance, it doesn't exist")
        super.putAll(self)
        setClean()
    }

    override fun setClean() {
        dirtyFields.clear()
        isPersisted = true
    }

    override fun isDirty() = dirtyFields.nextSetBit(0) != -1

    /** the key must be a field; the row becomes dirty, and volatile again if a key column changed */
    override fun put(key: String, value: Any?): Any? {
        val field = entity.fields[key] ?: throw SkormException("${entity.name} has no field named $key")
        val ret = super.put(key, value)
        if (isPersisted && field.isPrimary && ret != value) // CB TODO - since 'value' type is lax, 'value' may need a proper conversion before the comparison
                isPersisted = false
        dirtyFields.set(entity.fieldIndices[key]!!, true)
        return ret
    }

    override fun putAll(from: Map<out String, Any?>) = from.forEach { put(it.key, it.value) }

    // raw loading: database values under database names, filtered on the way in, nothing marked dirty

    @SkormInternalApi
    override fun putRawFields(from: Map<out String, Any?>) {
        from.entries.forEach {
            val fieldName = rawFieldName(it.key)
            entity.fields[fieldName]?.also { field ->
                putRawField(field, it.value)
            } ?: putRawValue(fieldName, it.value)
        }
    }

    @SkormInternalApi
    override fun putRawValue(key: String, value: Any?): Any? = super.put(key, value)

    @SkormInternalApi
    override fun putRawField(field: Field, value: Any?) {
        super.put(field.name, rawFieldValue(field, value))
    }

    override fun dirtyFieldNames(): Iterator<String> = object: Iterator<String> {
        var index = dirtyFields.nextSetBit(0)

        override operator fun hasNext() = (index != -1)
        override operator fun next(): String {
            return entity.fieldNames[index].also {
                index = dirtyFields.nextSetBit(index + 1)
            }
        }
    }
}
