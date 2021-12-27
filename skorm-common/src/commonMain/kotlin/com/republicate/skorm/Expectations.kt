package com.republicate.skorm

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
