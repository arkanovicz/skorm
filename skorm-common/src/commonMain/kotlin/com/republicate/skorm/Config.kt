package com.republicate.skorm

import com.republicate.kson.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.KProperty
import kotlin.text.split

open class Configuration(initial: Map<String, Any?>) {
    private val config = Json.MutableObject()

    init {
        putAll(initial)
    }

    constructor(vararg initial: Pair<String, Any?>) : this(initial.toMap())

    private fun putAll(map: Map<String, Any?>) {
        map.entries.forEach { (key, value) -> put(key, value) }
    }

    private fun put(key: String, value: Any?) {
        val fullPath = key.split('.')
        val leaf = fullPath.last()
        val path = fullPath.dropLast(1)
        var target = config
        path.forEach {
            val sub = target[it]
            if (sub == null) {
                val new = Json.MutableObject()
                target[it] = new
                target = new
            } else if (sub is Json.MutableObject) {
                target = sub
            } else {
                throw SkormException("Invalid mix of configuration properties path level for ${key}")
            }
        }
        target[leaf] = value
    }

    fun configure(key: String, value: Any?) = put(key, value)
    fun configure(values: Map<String, Any?>) = putAll(values)

    private fun <T> getInternal(key: String, getter: Json.MutableObject.(String)->T?): T? {
        key.split(".").let {
            var target = config
            for (i in 0 ..< it.size - 1) {
                target = target.getObject(it[i]) as Json.MutableObject? ?: return null
            }
            return target.getter(it.last())
        }
    }

    fun getString(key: String) = getInternal(key, Json.MutableObject::getString)
    fun getStrings(key: String) = getString(key)?.split(Regex("\\s*,\\s*"))
    fun getBoolean(key: String) = getInternal(key, Json.MutableObject::getBoolean)
    fun getInt(key: String) = getInternal(key, Json.MutableObject::getInt)
    fun getArray(key: String) = getInternal(key, Json.MutableObject::getArray)
    fun getObject(key: String) = getInternal(key, Json.MutableObject::getObject)

    override fun toString() = config.toString()
}

interface Configurable {
    val configTag: String? get() = null

    val config: Configuration

    fun configure(cfg: Map<String, Any?>) {
        config.configure(cfg)
    }
    @Throws(SkormException::class)
    fun initialize() {}

    @Throws(SkormException::class)
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

inline fun <reified T> Configurable.configPath(path: String, default: T? = null): ConfigDelegate<T> {
    val segments = path.split('.')
    val nestedConf: ()->Configuration = {
        val segments = path.split('.')
        var targetConfig = config
        for (i in 0..<segments.size - 1) {
            targetConfig = config.getObject(segments[i])?.let { Configuration(it) } ?: default as Configuration? ?: Configuration(Json.Object())
        }
        targetConfig
    }
    val key = segments.last()
    return when (T::class) {
        String::class -> ConfigDelegate { _, property -> (nestedConf().getString(key) ?: default) as T }
        Boolean::class -> ConfigDelegate { _, property -> (nestedConf().getBoolean(key) ?: default) as T }
        Int::class -> ConfigDelegate { _, property -> (nestedConf().getInt(key) ?: default) as T }
        Json.Array::class -> ConfigDelegate { _, property -> (nestedConf().getArray(key) ?: default) as T }
        Json.Object::class -> ConfigDelegate { _, property -> (nestedConf().getObject(key) ?: default) as T }
        else -> ConfigDelegate {
                thisRef, property -> throw SkormException("unhandled configuration type for property ${thisRef::class.simpleName}.${property.name}: ${T::class.simpleName}")
        }
    }
}
