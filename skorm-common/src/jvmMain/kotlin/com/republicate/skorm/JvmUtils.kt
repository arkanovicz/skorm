package com.republicate.skorm

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*

// actual typealias BitSet = java.util.BitSet

actual class BitSet actual constructor(size: Int): java.util.BitSet(size) {
    actual override operator fun get(index: Int) = super.get(index)
    actual override fun set(index: Int) = super.set(index)
    actual override fun set(index: Int, value: Boolean) = super.set(index, value)
    actual override fun clear(index: Int) = super.clear(index)
    actual fun or(another: BitSet) = super.or(another)
    actual override fun clear() = super.clear()
    actual fun nextSetBit() = nextSetBit(0)
    actual override fun nextSetBit(startIndex: Int) = super.nextSetBit(startIndex)
}

private val introspectionCache = ConcurrentHashMap<String, KFunction<*>>()
private val cacheMiss = object { fun cacheMiss() {} }::cacheMiss

private fun Any.findGenericGetter(): KFunction<*> {
    val key = this::class.qualifiedName ?: return cacheMiss
    return introspectionCache.computeIfAbsent(key) {
        val candidates = this::class.members.filter {
            it.name == "get" &&
                    it is KFunction<*>
        }.filter {
            val params = (it as KFunction<*>).parameters
            params.size == 2 &&
                    params[0].kind == KParameter.Kind.INSTANCE &&
                    params[1].kind == KParameter.Kind.VALUE
        }
        if (candidates.size == 1) candidates[0] as KFunction<*> else cacheMiss
    }
}
actual fun Any.hasGenericGetter() = findGenericGetter() != cacheMiss

actual fun Any.callGenericGetter(key: String) = findGenericGetter().call(key)
