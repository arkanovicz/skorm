//@file:JvmName("Utils")
//@file:JvmMultifileClass

package com.republicate.skorm

import kotlin.reflect.KProperty
import kotlinx.atomicfu.*
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

class MandatoryMap<K, V>(private val map: Map<K, V>) : Map<K, V> by map {
    override operator fun get(key: K): V {
        return map[key] ?: throw SQLException("missing key: $key")
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
inline fun <K, V> Map<K, V>.getOrElseNullable(key: K, defaultValue: () -> V): V {
    val value = get(key)
    return if (value == null && !containsKey(key)) {
        defaultValue()
    } else {
        value as V
    }
}

expect open class SQLException : Exception {
    constructor()
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
    constructor(cause: Throwable?)
}

// BitSet needs an explicit size for now
// See https://youtrack.jetbrains.com/issue/KT-42615
const val MAX_FIELDS = 64

expect class BitSet(size: Int) {
    operator fun get(index: Int): Boolean
    fun set(index: Int, value: Boolean)
    fun clear(index: Int)
    fun or(another: BitSet)
    fun clear()
}
