package com.republicate.skorm

actual typealias ConcurrentMap<K, V> = java.util.concurrent.ConcurrentHashMap<K, V>

actual fun <K, V> concurrentMapOf(vararg elements: Pair<K, V>) =  java.util.concurrent.ConcurrentHashMap<K, V>().apply {
    putAll(elements)
}
