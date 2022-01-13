package com.republicate.skorm

import kotlin.reflect.KProperty
import kotlinx.atomicfu.*

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
