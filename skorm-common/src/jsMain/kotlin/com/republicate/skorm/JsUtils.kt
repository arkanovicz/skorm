package com.republicate.skorm

actual class BitSet actual constructor(size: Int) {

    private val bits = Array(size) { false }

    actual operator fun get(index: Int) = bits[index]
    actual fun set(index: Int, value: Boolean) {
        bits[index] = value
    }

    actual fun clear(index: Int) {
        bits[index] = false
    }

    actual fun or(another: BitSet) {
        for (index in 0..bits.size - 1) {
            bits[index] =  bits[index] || another.bits[index]
        }
    }

    actual fun clear() {
        for (index in 0..bits.size - 1) {
            bits[index] = false
        }
    }

    actual fun nextSetBit(startIndex: Int): Int {
        for (index in startIndex .. bits.size - 1) {
            if (bits[index]) return index
        }
        return -1
    }
}

actual fun Any.hasGenericGetter(): Boolean {
    // everything in JS has a generic getter...
    return true
}

actual fun Any.callGenericGetter(key: String) = js("this[key]")

//actual open class SQLException actual constructor(message: String?, cause: Throwable?): Exception(message, cause) {
//    actual constructor(): this(null, null)
//    actual constructor(message: String?): this(message, null)
//    actual constructor(cause: Throwable?): this(null, cause)
//}
