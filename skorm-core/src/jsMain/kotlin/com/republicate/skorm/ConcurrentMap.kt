package com.republicate.skorm

actual typealias ConcurrentMap<K, V> = LinkedHashMap<K, V>

actual fun <K, V> concurrentMapOf(vararg elements: Pair<K, V>) = linkedMapOf(*elements)
