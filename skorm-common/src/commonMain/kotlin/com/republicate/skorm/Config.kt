package com.republicate.skorm

import com.republicate.kson.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KProperty

// CB TODO - upstream - Json.MutableObject
open class Configuration private constructor(private val config: Json.MutableObject) {
    val values = Json.Object(config) // no-copy constructor, changes will be reflected

    constructor() : this(mutableMapOf())
    constructor(initial: Map<String, Any?>) : this(Json.MutableObject().apply { putAll(initial) })
    constructor(vararg initial: Pair<String, Any?>) : this(initial.toMap())

    fun configure(values: Map<String, Any?>) = config.putAll(values)

    fun getString(key: String) = values.getString(key)
    fun getBoolean(key: String) = values.getBoolean(key)
    fun getInt(key: String) = values.getInt(key)
    fun getArray(key: String) = values.getArray(key)
    fun getObject(key: String) = values.getObject(key)
}

interface Configurable {
    val configTag: String? get() = null
    val config: Configuration
    fun configure(cfg: Map<String, Any?>) {
        config.configure(cfg)
    }
    @Throws(SkormException::class)
    fun initialize()
    fun initialize(cfg: Map<String, Any?>?) {
        if (cfg != null) configure(cfg)
        initialize()
    }
}

fun interface ConfigDelegate<T> {
    operator fun getValue(thisRef: Any, property: KProperty<*>): T
}

inline fun <reified T> Configurable.config(default: T? = null): ConfigDelegate<T> {
    return when (T::class) {
        String::class -> ConfigDelegate { _, property -> (config.getString(property.name) ?: default) as T }
        Boolean::class -> ConfigDelegate { _, property -> (config.getBoolean(property.name) ?: default) as T }
        Int::class -> ConfigDelegate { _, property -> (config.getInt(property.name) ?: default) as T }
        Json.Array::class -> ConfigDelegate { _, property -> (config.getArray(property.name) ?: default) as T }
        Json.Object::class -> ConfigDelegate { _, property -> (config.getObject(property.name) ?: default) as T }
        else -> ConfigDelegate {
                thisRef, property -> throw SkormException("unhandled configuration type for property ${thisRef::class.simpleName}.${property.name}: ${T::class.simpleName}")
        }
    }
}
