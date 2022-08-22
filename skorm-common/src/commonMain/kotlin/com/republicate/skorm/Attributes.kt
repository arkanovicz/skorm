package com.republicate.skorm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.republicate.kson.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import mu.KotlinLogging

/*
 * Attributes
 */

internal val logger = KotlinLogging.logger("skorm.common")

// CB TODO - forbidden only on entity
val reserveAttributeNames = setOf("insert", "fetch", "update", "delete")

expect fun Any.hasGenericGetter(): Boolean
expect fun Any.callGenericGetter(key: String): Any?

sealed class Attribute<out T>(val name: String, private val parameters: Set<String> = emptySet(), val resultEntity: Entity? = null,  private val useDirtyFields: Boolean = false) {

    /**
     * Match attribute named parameters with values found in provided context parameters
     */
    fun matchParamValues(vararg rawValues: Any?) : Map<String, Any?> {

        logger.info { ">> matchParamValues ${rawValues.joinToString { "$it" }}" }

        val ret = mutableMapOf<String, Any?>()

        val params: Set<String> =
            if (useDirtyFields) {
                if (rawValues.isEmpty() ||
                    rawValues.first() !is Instance ||
                    rawValues.size > 2 ||
                    rawValues.size == 2 && rawValues.last() !is GeneratedKeyMarker
                ) {
                    throw SkormException("Attributes based on dirty fields are only valid with a single instance parameter or receiver")
                }
                val instance = rawValues.first() as Instance
                if (rawValues.size == 2) ret[GeneratedKeyMarker.PARAM_KEY] = rawValues.last()
                instance.dirtyFieldNames().asSequence().toSet()
            } else {
                parameters
            }

        val consumedParam = BitSet(rawValues.size)

        logger.info { "-- parameters size = ${parameters.size}"}
        params.forEach { p ->
            logger.info { "Searching for $p"}
            params@ for (i in rawValues.indices) {
                logger.info { "at indice $i"}
                val value = rawValues[i]
                when(value) {
                    is Instance -> {
                        logger.info { "  found instance"}
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
                            logger.info { "  found inside instance: $found"}
//                            ret[p] = value.entity.fields[p]?.write(found) ?: found
                            ret[p] = found
                            break@params
                        }
                    }
                    is Map<*, *> -> {
                        logger.info { "  found map"}
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
                            logger.info { "  found inside map: $found"}
                            ret[p] = found
                            break@params
                        }
                    }
                    hasGenericGetter() -> {
                        logger.info { "  found getter"}
                        val found = value.callGenericGetter(p)
                        if (found != null) {
                            logger.info { "  found with getter: $found"}
                            ret[p] = found
                            break@params
                        }
                    }
                    is GeneratedKeyMarker -> {
                        ret[GeneratedKeyMarker.PARAM_KEY] = value
                        break@params
                    }
                    else -> {
                        logger.info { "  searching among raw params"}
                        if (!consumedParam[i]) {
                            logger.info { "  consuming raw param $i: $value"}
                            ret[p] = value
                            consumedParam.set(i, true)
                            break@params
                        }
                    }
                }
                logger.info { "  not found at indice $i. " }
                if (i + 1 == rawValues.size) {
                    throw SkormException("attribute $name: parameter $p not found")
                }
            }
        }
        // TODO control that all simple parameters have been consumed
        // rawValues.filter { it !is Map<*, *> }.size > consumedParam.setBitsCount => error
        logger.info { "<< matchParamValues { ${ret.entries.joinToString(",") } }" }
        return ret
    }

    @Suppress("Unchecked_cast")
    open fun handleResult(result: Any?): T = result as T
}

sealed class ScalarAttribute<T>(name: String, parameters: Set<String>):
    Attribute<T>(name, parameters)

class StringAttribute(name: String, parameters: Set<String>): ScalarAttribute<String>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toString(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableStringAttribute(name: String, parameters: Set<String>): ScalarAttribute<String?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toString(result)
}

class CharAttribute(name: String, parameters: Set<String>): ScalarAttribute<Char>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toChar(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableCharAttribute(name: String, parameters: Set<String>): ScalarAttribute<Char?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toChar(result)
}

class BooleanAttribute(name: String, parameters: Set<String>): ScalarAttribute<Boolean>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBoolean(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBooleanAttribute(name: String, parameters: Set<String>): ScalarAttribute<Boolean?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBoolean(result)
}

class ByteAttribute(name: String, parameters: Set<String>): ScalarAttribute<Byte>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toByte(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableByteAttribute(name: String, parameters: Set<String>): ScalarAttribute<Byte?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toByte(result)
}

class ShortAttribute(name: String, parameters: Set<String>): ScalarAttribute<Short>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toShort(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableShortAttribute(name: String, parameters: Set<String>): ScalarAttribute<Short?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toShort(result)
}

class IntAttribute(name: String, parameters: Set<String>): ScalarAttribute<Int>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toInt(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableIntAttribute(name: String, parameters: Set<String>): ScalarAttribute<Int?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toInt(result)
}

class LongAttribute(name: String, parameters: Set<String>): ScalarAttribute<Long>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLong(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLongAttribute(name: String, parameters: Set<String>): ScalarAttribute<Long?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLong(result)
}

class BigIntegerAttribute(name: String, parameters: Set<String>): ScalarAttribute<BigInteger>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBigInteger(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBigIntegerAttribute(name: String, parameters: Set<String>): ScalarAttribute<BigInteger?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBigInteger(result)
}

class FloatAttribute(name: String, parameters: Set<String>): ScalarAttribute<Float>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toFloat(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableFloatAttribute(name: String, parameters: Set<String>): ScalarAttribute<Float?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toFloat(result)
}

class DoubleAttribute(name: String, parameters: Set<String>): ScalarAttribute<Double>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toDouble(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableDoubleAttribute(name: String, parameters: Set<String>): ScalarAttribute<Double?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toDouble(result)
}

class BigDecimalAttribute(name: String, parameters: Set<String>): ScalarAttribute<BigDecimal>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBigDecimal(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBigDecimalAttribute(name: String, parameters: Set<String>): ScalarAttribute<BigDecimal?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBigDecimal(result)
}

class InstantAttribute(name: String, parameters: Set<String>): ScalarAttribute<Instant>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toInstant(result)  ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableInstantAttribute(name: String, parameters: Set<String>): ScalarAttribute<Instant?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toInstant(result)
}

class LocalTimeAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalTime>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalTime(result) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalTimeAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalTime?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalTime(result)
}

class LocalDateTimeAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalDateTime>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalDateTime(result) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalDateTimeAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalDateTime?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalDateTime(result)
}

class LocalDateAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalDate>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalDate(result) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableLocalDateAttribute(name: String, parameters: Set<String>): ScalarAttribute<LocalDate?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toLocalDate(result)
}

class BytesAttribute(name: String, parameters: Set<String>): ScalarAttribute<ByteArray>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBytes(result) ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableBytesAttribute(name: String, parameters: Set<String>): ScalarAttribute<ByteArray?>(name, parameters) {
    override fun handleResult(result: Any?) = Json.TypeUtils.toBytes(result)
}

class RowAttribute(name: String, parameters: Set<String>): Attribute<Json.Object>(name, parameters) {
    override fun handleResult(result: Any?) =
        result as Json.Object? ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableRowAttribute(name: String, parameters: Set<String>): Attribute<Json.Object?>(name, parameters)

class InstanceAttribute<out T: Instance>(name: String, parameters: Set<String>, resultEntity: Entity): Attribute<T>(name, parameters, resultEntity = resultEntity) {
    override fun handleResult(result: Any?) =
        result as T? ?: throw SkormException("attribute $name cannot have a null result")
}

class NullableInstanceAttribute<out T: Instance>(name: String, parameters: Set<String>, resultEntity: Entity): Attribute<T?>(name, parameters, resultEntity = resultEntity)

class RowSetAttribute(name: String, parameters: Set<String>): Attribute<Sequence<Json.Object>>(name, parameters)

class BagAttribute<out T: Instance>(name: String, parameters: Set<String>, resultEntity: Entity): Attribute<Sequence<T>>(name, parameters, resultEntity = resultEntity)
    
class MutationAttribute(name: String, parameters: Set<String> = setOf(), useDirtyFields: Boolean = false): Attribute<Long>(name, parameters, useDirtyFields = useDirtyFields)

// WIP
class TransactionAttribute(name: String, parameters: Set<String> = setOf(), useDirtyFields: Boolean = false): Attribute<Long>(name, parameters, useDirtyFields = useDirtyFields)

/*
 * Attributes holder
 */

abstract class AttributeHolder(val name: String, val parent: AttributeHolder? = null) {
    abstract val processor: Processor
    open val schema: Schema? = null
    private val _attributes = mutableMapOf<String, Attribute<*>>()
    val attributes: Map<String, Attribute<*>> get() = _attributes
    val path: String by lazy { (parent?.path ?: "") + "/$name" }

    inline fun <reified A> getAttribute(attrName: String): A? {
        val attr = attributes[attrName]
        return when {
            A::class.isInstance(attr) -> attr as A
            attr == null -> null
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

    open fun prepare(attr: Attribute<*>, vararg params: Any?) = Pair("$path/${attr.name}", attr.matchParamValues(*params))

    suspend inline fun <reified T> eval(attrName: String, vararg params: Any?) = eval(findAttribute<T>(attrName), *params)

    suspend inline fun <reified T> eval(attribute: Attribute<T>, vararg params: Any?): T {
        val (execPath, execParams) = prepare(attribute, *params)
        return attribute.handleResult(processor.eval(execPath, execParams))
    }

    suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) = retrieve(findAttribute<T>(attrName), *params)

    suspend inline fun <reified T: Json.Object?> retrieve(attribute: Attribute<T>, vararg params: Any?): T {
        val (execPath, execParams) = prepare(attribute, *params)
        return attribute.handleResult(processor.retrieve(execPath, execParams, attribute.resultEntity))
    }

    suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) = query(findAttribute<Sequence<T>>(attrName), *params)

    suspend inline fun <reified T: Json.Object> query(attribute: Attribute<Sequence<T>>, vararg params: Any?): Sequence<T> {
        val (execPath, execParams) = prepare(attribute, *params)
        return attribute.handleResult(processor.query(execPath, execParams, attribute.resultEntity))
    }

    suspend fun perform(attrName: String, vararg params: Any?) = perform(findAttribute<Long>(attrName), *params)

    suspend fun perform(attribute: Attribute<Long>, vararg params: Any?): Long {
        val (execPath, execParams) = prepare(attribute, *params)
        return attribute.handleResult(processor.perform(execPath, execParams))
    }

    // WIP
//    suspend fun attempt(attrName: String, vararg params: Any?) =
//        findAttribute<List<Int>>(attrName).execute(*params)
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> AttributeHolder.scalarAttribute(name: String, params: Set<String>): ScalarAttribute<T> {
    return if (null is T) when (T::class) {
        String::class -> NullableStringAttribute(name, params) as ScalarAttribute<T>
        Char::class -> NullableCharAttribute(name, params) as ScalarAttribute<T>
        Boolean::class -> NullableBooleanAttribute(name, params) as ScalarAttribute<T>
        Byte::class -> NullableByteAttribute(name, params) as ScalarAttribute<T>
        Short::class -> NullableShortAttribute(name, params) as ScalarAttribute<T>
        Int::class -> NullableIntAttribute(name, params) as ScalarAttribute<T>
        Long::class -> NullableLongAttribute(name, params) as ScalarAttribute<T>
        BigInteger::class -> NullableBigIntegerAttribute(name, params) as ScalarAttribute<T>
        Float::class -> NullableFloatAttribute(name, params) as ScalarAttribute<T>
        Double::class -> NullableDoubleAttribute(name, params) as ScalarAttribute<T>
        BigDecimal::class -> NullableBigDecimalAttribute(name, params) as ScalarAttribute<T>
        Instant::class -> NullableInstantAttribute(name, params) as ScalarAttribute<T>
        LocalTime::class -> NullableLocalTimeAttribute(name, params) as ScalarAttribute<T>
        LocalDate::class -> NullableLocalDateAttribute(name, params) as ScalarAttribute<T>
        LocalDateTime::class -> NullableLocalDateTimeAttribute(name, params) as ScalarAttribute<T>
        ByteArray::class -> NullableBytesAttribute(name, params) as ScalarAttribute<T>
        // not supported yet
        // else -> throw SkormException("unhandled type: ${T::class.qualifiedName}")
        else -> throw SkormException("unhandled type: ${T::class.simpleName}")
    } else when (T::class) {
        String::class -> StringAttribute(name, params) as ScalarAttribute<T>
        Char::class -> CharAttribute(name, params) as ScalarAttribute<T>
        Boolean::class -> BooleanAttribute(name, params) as ScalarAttribute<T>
        Byte::class -> ByteAttribute(name, params) as ScalarAttribute<T>
        Short::class -> ShortAttribute(name, params) as ScalarAttribute<T>
        Int::class -> IntAttribute(name, params) as ScalarAttribute<T>
        Long::class -> LongAttribute(name, params) as ScalarAttribute<T>
        BigInteger::class -> BigIntegerAttribute(name, params) as ScalarAttribute<T>
        Float::class -> FloatAttribute(name, params) as ScalarAttribute<T>
        Double::class -> DoubleAttribute(name, params) as ScalarAttribute<T>
        BigDecimal::class -> BigDecimalAttribute(name, params) as ScalarAttribute<T>
        Instant::class -> InstantAttribute(name, params) as ScalarAttribute<T>
        LocalTime::class -> LocalTimeAttribute(name, params) as ScalarAttribute<T>
        LocalDate::class -> LocalDateAttribute(name, params) as ScalarAttribute<T>
        LocalDateTime::class -> LocalDateTimeAttribute(name, params) as ScalarAttribute<T>
        ByteArray::class -> BytesAttribute(name, params) as ScalarAttribute<T>
        // not supported yet
        // else -> throw SkormException("unhandled type: ${T::class.qualifiedName}")
        else -> throw SkormException("unhandled type: ${T::class.simpleName}")
    }.also {
        addAttribute(it)
    }
}

fun AttributeHolder.rowAttribute(name: String, params: Set<String>): RowAttribute =
    RowAttribute(name, params).also {
        addAttribute(it)
    }

fun AttributeHolder.nullableRowAttribute(name: String, params: Set<String>): NullableRowAttribute =
    NullableRowAttribute(name, params).also {
        addAttribute(it)
    }

fun <T: Instance>AttributeHolder.instanceAttribute(name: String, resultEntity: Entity, params: Set<String>): InstanceAttribute<T> =
    InstanceAttribute<T>(name, params, resultEntity).also {
        addAttribute(it)
    }

fun <T: Instance>AttributeHolder.nullableInstanceAttribute(name: String, resultEntity: Entity, params: Set<String>): NullableInstanceAttribute<T> =
    NullableInstanceAttribute<T>(name, params, resultEntity).also {
        addAttribute(it)
    }

fun AttributeHolder.rowSetAttribute(name: String, params: Set<String>): RowSetAttribute =
    RowSetAttribute(name, params).also {
        addAttribute(it)
    }

fun <T: Instance>AttributeHolder.bagAttribute(name: String, resultEntity: Entity, params: Set<String>): BagAttribute<T> =
    BagAttribute<T>(name, params, resultEntity).also {
        addAttribute(it)
    }

fun AttributeHolder.mutationAttribute(name: String, params: Set<String>): MutationAttribute =
    MutationAttribute(name, params).also {
        addAttribute(it)
    }
