package com.republicate.skorm

// actual typealias BitSet = kotlin.native.BitSet

@OptIn(ObsoleteNativeApi::class)
actual class BitSet actual constructor(size: Int) {
    private var bits = kotlin.native.BitSet(size)
    actual operator fun get(index: Int) = bits.get(index)
    actual fun set(index: Int) = set(index, true)
    actual fun set(index: Int, value: Boolean) = bits.set(index, value)
    actual fun clear(index: Int) = bits.clear(index)
    actual fun or(another: BitSet) = bits.or(another.bits)
    actual fun clear() = bits.clear()
    actual fun nextSetBit() = nextSetBit(0)
    actual fun nextSetBit(startIndex: Int) = bits.nextSetBit(startIndex)
}

actual fun Any.hasGenericGetter(): Boolean {
    // no introspection in native
    return false
}

actual fun Any.callGenericGetter(key: String): Any? {
    return null
}

//actual open class SQLException actual constructor(message: String?, cause: Throwable?): Exception(message, cause) {
//    actual constructor(): this(null, null)
//    actual constructor(message: String?): this(message, null)
//    actual constructor(cause: Throwable?): this(null, cause)
//}
