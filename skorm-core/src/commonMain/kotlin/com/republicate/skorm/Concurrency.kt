package com.republicate.skorm

expect class ConcurrentMap<K, V>(): MutableMap<K, V>

expect fun <K, V> concurrentMapOf(vararg elements: Pair<K, V>): ConcurrentMap<K, V>
