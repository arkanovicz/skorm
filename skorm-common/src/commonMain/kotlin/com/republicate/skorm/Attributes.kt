package com.republicate.skorm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.republicate.kson.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/*
 * Attributes
 */

// CB TODO - forbidden only on entity
val reserveAttributeNames = setOf("insert", "fetch", "update", "delete")

expect fun Any.hasGenericGetter(): Boolean
expect fun Any.callGenericGetter(key: String): Any?

sealed class Attribute<out T>(val holder: AttributeHolder, val name: String, val parameters: Set<String> = emptySet(), val useDirtyFields: Boolean = false) {

    /**
     * Match attribute named parameters with values found in provided context parameters
     */
    internal fun matchParamValues(vararg rawValues: Any?) : Map<String, Any?> {
        val ret = mutableMapOf<String, Any?>()

        val params: Set<String> =
            if (useDirtyFields) {
                if (rawValues.size != 1 || rawValues.first() !is Instance) {
                    throw SkormException("Attributes based on dirty fields are only valid with a single instance parameter or receiver")
                }
                val instance = rawValues.first() as Instance
                instance.dirtyFieldNames().asSequence().toSet()
            } else {
                parameters
            }

        val consumedParam = BitSet(rawValues.size)

        params.forEach { p ->
            params@ for (i in rawValues.indices) {
                val value = rawValues[i]
                when(value) {
                    is Instance -> {
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
//                            ret[p] = value.entity.fields[p]?.write(found) ?: found
                            ret[p] = found
                            break@params
                        }
                    }
                    is Map<*, *> -> {
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
                            ret[p] = found
                            break@params
                        }
                    }
                    hasGenericGetter() -> {
                        val found = value.callGenericGetter(p)
                        if (found != null) {
                            ret[p] = found
                            break@params
                        }
                    }
                    is GeneratedKeyMarker -> {
//                        ret[value.colName] = ??
                    }
                    else -> if (!consumedParam[i]) {
                        ret[p] = value
                        consumedParam.set(i, true)
                        break@params
                    }
                }
                if (i + 1 == parameters.size) {
                    throw SkormException("attribute $name: parameter $p not found")
                }
            }
        }
        // TODO control that all simple parameters have been consumed
        // rawValues.filter { it !is Map<*, *> }.size > consumedParam.setBitsCount => error
        return ret
    }

    abstract suspend fun execute(vararg params: Any?): T
}

sealed class ScalarAttribute<T>(holder: AttributeHolder, name: String, parameters: Set<String>):
    Attribute<T>(holder, name, parameters) {
    suspend fun exec(vararg params: Any?) =
        holder.processor.eval("${holder.path}/$name", matchParamValues(*params))
}

class StringAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<String>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toString(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableStringAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<String?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toString(exec(*params))
}

class CharAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Char>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toChar(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableCharAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Char?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toChar(exec(*params))
}

class BooleanAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Boolean>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBoolean(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBooleanAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Boolean?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBoolean(exec(*params))
}

class ByteAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Byte>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toByte(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableByteAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Byte?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toByte(exec(*params))
}

class ShortAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Short>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toShort(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableShortAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Short?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toShort(exec(*params))
}

class IntAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Int>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toInt(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableIntAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Int?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toInt(exec(*params))
}

class LongAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Long>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLong(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLongAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Long?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLong(exec(*params))
}

class BigIntegerAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<BigInteger>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBigInteger(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBigIntegerAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<BigInteger?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBigInteger(exec(*params))
}

class FloatAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Float>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toFloat(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableFloatAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Float?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toFloat(exec(*params))
}

class DoubleAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Double>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toDouble(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableDoubleAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Double?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toDouble(exec(*params))
}

class BigDecimalAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<BigDecimal>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBigDecimal(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBigDecimalAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<BigDecimal?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBigDecimal(exec(*params))
}

class InstantAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Instant>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toInstant(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableInstantAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<Instant?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toInstant(exec(*params))
}

class LocalTimeAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalTime>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalTime(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalTimeAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalTime?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalTime(exec(*params))
}

class LocalDateTimeAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalDateTime>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalDateTime(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalDateTimeAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalDateTime?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalDateTime(exec(*params))
}

class LocalDateAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalDate>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalDate(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalDateAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<LocalDate?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toLocalDate(exec(*params))
}

class BytesAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<ByteArray>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBytes(exec(*params)) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBytesAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): ScalarAttribute<ByteArray?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) = Json.TypeUtils.toBytes(exec(*params))
}

class RowAttribute(holder: AttributeHolder, name: String, parameters: Set<String>):
    Attribute<Json.Object>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.retrieve("${holder.path}/$name", matchParamValues(*params))
            ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableRowAttribute(holder: AttributeHolder, name: String, parameters: Set<String>):
    Attribute<Json.Object?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.retrieve("${holder.path}/$name", matchParamValues(*params))
}

class InstanceAttribute(holder: AttributeHolder, name: String, parameters: Set<String>, val resultEntity: Entity):
    Attribute<Instance>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.retrieve("${holder.path}/$name", matchParamValues(*params), resultEntity) as Instance?
            ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableInstanceAttribute(holder: AttributeHolder, name: String, parameters: Set<String>, val resultEntity: Entity):
    Attribute<Instance?>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.retrieve("${holder.path}/$name", matchParamValues(*params), resultEntity) as Instance?
}

class RowSetAttribute(holder: AttributeHolder, name: String, parameters: Set<String>): Attribute<Sequence<Json.Object>>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.query("${holder.path}/$name", matchParamValues(*params))
}

class BagAttribute(holder: AttributeHolder, name: String, parameters: Set<String>, val resultEntity: Entity): Attribute<Sequence<Instance>>(holder, name, parameters) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.query("${holder.path}/$name", matchParamValues(*params), resultEntity) as Sequence<Instance>
}

class MutationAttribute(holder: AttributeHolder, name: String, parameters: Set<String> = setOf(), useDirtyFields: Boolean = false): Attribute<Long>(holder, name, parameters, useDirtyFields) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.perform("${holder.path}/$name", matchParamValues(*params))
}

/*
 * Attributes holder
 */

abstract class AttributeHolder(val name: String, val parent: AttributeHolder? = null) {
    abstract val processor: Processor
    private val _attributes = mutableMapOf<String, Attribute<*>>()
    val attributes: Map<String, Attribute<*>> get() = _attributes
    val path: String by lazy { (parent?.path ?: "") + "/$name" }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified A> getAttribute(attrName: String): A? {
        val attr = attributes[attrName]
        when {
            A::class.isInstance(attr) -> return attr as A
            attr == null -> return null
            else -> throw SkormException("attribute $path.$attrName is not a ${A::class::simpleName}")
        }
    }

    inline fun <reified T> findAttribute(attrName: String): Attribute<T> {
        var holder = this
        while (true) {
            val attr = holder.getAttribute<Attribute<T>>(attrName)
            if (attr != null) return attr
            holder = holder.parent ?: throw SkormException("attribute not found: $path.$attrName")
        }
    }

    fun addAttribute(attr: Attribute<*>) {
        val previous = _attributes.put(attr.name, attr)
        if (previous != null) throw SkormException("attribute $path.${attr.name} cannot be overwritten")
    }

    suspend inline fun <reified T> eval(attrName: String, vararg params: Any?) =
        findAttribute<T>(attrName).execute(*params)

    suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) =
        findAttribute<T>(attrName).execute(*params)

    suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) =
        findAttribute<Sequence<T>>(attrName).execute(*params)

    suspend fun perform(attrName: String, vararg params: Any?) =
        findAttribute<Long>(attrName).execute(*params)

    suspend fun attempt(attrName: String, vararg params: Any?) =
        findAttribute<List<Int>>(attrName).execute(*params)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> AttributeHolder.scalarAttribute(name: String, params: Set<String>): ScalarAttribute<T> {
    return if (null is T) when (T::class) {
        String::class -> NullableStringAttribute(this, name, params) as ScalarAttribute<T>
        Char::class -> NullableCharAttribute(this, name, params) as ScalarAttribute<T>
        Boolean::class -> NullableBooleanAttribute(this, name, params) as ScalarAttribute<T>
        Byte::class -> NullableByteAttribute(this, name, params) as ScalarAttribute<T>
        Short::class -> NullableShortAttribute(this, name, params) as ScalarAttribute<T>
        Int::class -> NullableIntAttribute(this, name, params) as ScalarAttribute<T>
        Long::class -> NullableLongAttribute(this, name, params) as ScalarAttribute<T>
        BigInteger::class -> NullableBigIntegerAttribute(this, name, params) as ScalarAttribute<T>
        Float::class -> NullableFloatAttribute(this, name, params) as ScalarAttribute<T>
        Double::class -> NullableDoubleAttribute(this, name, params) as ScalarAttribute<T>
        BigDecimal::class -> NullableBigDecimalAttribute(this, name, params) as ScalarAttribute<T>
        Instant::class -> NullableInstantAttribute(this, name, params) as ScalarAttribute<T>
        LocalTime::class -> NullableLocalTimeAttribute(this, name, params) as ScalarAttribute<T>
        LocalDate::class -> NullableLocalDateAttribute(this, name, params) as ScalarAttribute<T>
        LocalDateTime::class -> NullableLocalDateTimeAttribute(this, name, params) as ScalarAttribute<T>
        ByteArray::class -> NullableBytesAttribute(this, name, params) as ScalarAttribute<T>
        // not supported yet
        // else -> throw SkormException("unhandled type: ${T::class.qualifiedName}")
        else -> throw SkormException("unhandled type: ${T::class.simpleName}")
    } else when (T::class) {
        String::class -> StringAttribute(this, name, params) as ScalarAttribute<T>
        Char::class -> CharAttribute(this, name, params) as ScalarAttribute<T>
        Boolean::class -> BooleanAttribute(this, name, params) as ScalarAttribute<T>
        Byte::class -> ByteAttribute(this, name, params) as ScalarAttribute<T>
        Short::class -> ShortAttribute(this, name, params) as ScalarAttribute<T>
        Int::class -> IntAttribute(this, name, params) as ScalarAttribute<T>
        Long::class -> LongAttribute(this, name, params) as ScalarAttribute<T>
        BigInteger::class -> BigIntegerAttribute(this, name, params) as ScalarAttribute<T>
        Float::class -> FloatAttribute(this, name, params) as ScalarAttribute<T>
        Double::class -> DoubleAttribute(this, name, params) as ScalarAttribute<T>
        BigDecimal::class -> BigDecimalAttribute(this, name, params) as ScalarAttribute<T>
        Instant::class -> InstantAttribute(this, name, params) as ScalarAttribute<T>
        LocalTime::class -> LocalTimeAttribute(this, name, params) as ScalarAttribute<T>
        LocalDate::class -> LocalDateAttribute(this, name, params) as ScalarAttribute<T>
        LocalDateTime::class -> LocalDateTimeAttribute(this, name, params) as ScalarAttribute<T>
        ByteArray::class -> BytesAttribute(this, name, params) as ScalarAttribute<T>
        // not supported yet
        // else -> throw SkormException("unhandled type: ${T::class.qualifiedName}")
        else -> throw SkormException("unhandled type: ${T::class.simpleName}")
    }.also {
        addAttribute(it)
    }
}

fun AttributeHolder.rowAttribute(name: String, params: Set<String>): RowAttribute =
    RowAttribute(this, name, params).also {
        addAttribute(it)
    }

fun AttributeHolder.nullableRowAttribute(name: String, params: Set<String>): NullableRowAttribute =
    NullableRowAttribute(this, name, params).also {
        addAttribute(it)
    }

fun AttributeHolder.instanceAttribute(name: String, resultEntity: Entity, params: Set<String>): InstanceAttribute =
    InstanceAttribute(this, name, params, resultEntity).also {
        addAttribute(it)
    }

fun AttributeHolder.nullableInstanceAttribute(name: String, resultEntity: Entity, params: Set<String>): NullableInstanceAttribute =
    NullableInstanceAttribute(this, name, params, resultEntity).also {
        addAttribute(it)
    }

fun AttributeHolder.rowSetAttribute(name: String, params: Set<String>): RowSetAttribute =
    RowSetAttribute(this, name, params).also {
        addAttribute(it)
    }

fun AttributeHolder.bagAttribute(name: String, resultEntity: Entity, params: Set<String>): BagAttribute =
    BagAttribute(this, name, params, resultEntity).also {
        addAttribute(it)
    }

fun AttributeHolder.mutationAttribute(name: String, params: Set<String>): MutationAttribute =
    MutationAttribute(this, name, params).also {
        addAttribute(it)
    }
