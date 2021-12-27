package com.republicate.skorm

class MandatoryMap<K, V>(private val map: Map<K, V>) : Map<K, V> by map {
    override operator fun get(key: K): V {
        return map[key] ?: throw SQLException("missing key: $key")
    }
}
