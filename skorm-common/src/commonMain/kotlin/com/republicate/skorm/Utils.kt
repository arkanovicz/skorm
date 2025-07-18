//@file:JvmName("Utils")
//@file:JvmMultifileClass

package com.republicate.skorm

import kotlin.reflect.KProperty
import kotlinx.atomicfu.*

class MandatoryMap<K, V>(private val map: Map<K, V>) : Map<K, V> by map {
    override operator fun get(key: K): V {
        return map[key] ?: throw SkormException("missing key: $key")
    }
}

class InitOnce<V> {
    // let's assume *null* means *uninitialized*
    private val value = atomic<V?>(null)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V? {
        return value.value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: V?) {
        if (!value.compareAndSet(null, newValue)) {
            throw IllegalStateException("variable already initialized")

        }
    }
}

class NotNullInitOnce<V>(private val defaultValue: V) {
    private val value = atomic<V>(defaultValue)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return value.value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: V) {
        if (!value.compareAndSet(defaultValue, newValue)) {
            throw IllegalStateException("variable already initialized")

        }
    }
}

inline fun <reified V> initOnce() = InitOnce<V>()
inline fun <reified V> initOnce(defaultValue: V) = NotNullInitOnce<V>(defaultValue)

// getOrElse assimilates null and missing values
// See Maps.kt and https://youtrack.jetbrains.com/issue/KT-21392
// Two lookups in some cases, though.
@Suppress("UNCHECKED_CAST")
inline fun <K, V> Map<K, V>.getOrElseNullable(key: K, defaultValue: () -> V): V {
    val value = get(key)
    return if (value == null && !containsKey(key)) {
        defaultValue()
    } else {
        value as V
    }
}

// BitSet needs an explicit size for now
// See https://youtrack.jetbrains.com/issue/KT-42615
const val MAX_FIELDS = 64

expect class BitSet(size: Int) {
    operator fun get(index: Int): Boolean
    fun set(index: Int)
    fun set(index: Int, value: Boolean)
    fun clear(index: Int)
    fun or(another: BitSet)
    fun clear()
    fun nextSetBit(): Int
    fun nextSetBit(startIndex: Int): Int
}

// No way to access this class from JVM... CB TODO
//expect open class SQLException : Exception {
//    constructor()
//    constructor(message: String?)
//    constructor(message: String?, cause: Throwable?)
//    constructor(cause: Throwable?)
//}

open class Transformers<String, U>(val transformers: Map<String, (U)->U>, val parent: Transformers<String, U>? = null) {
    val customTransformers = mutableMapOf<String, (U)->U>()
    operator fun get(name: String): (U)->U =
        customTransformers[name] ?: transformers[name] ?: parent?.get(name) ?: throw SkormException("Missing transformer $name")
    operator fun set(name: String, op: (U)->U) {
        customTransformers[name] = op
    }
}

fun String.withCapital() = replaceFirstChar { it.uppercase() }
fun String.withoutCapital() = replaceFirstChar { it.lowercase() }
