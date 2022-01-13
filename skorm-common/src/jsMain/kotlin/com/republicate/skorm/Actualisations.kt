package com.republicate.skorm

actual class BitSet actual constructor(size: Int) {
    actual operator fun get(index: Int): Boolean {
        TODO("Not yet implemented")
    }

    actual fun set(index: Int, value: Boolean) {
    }

    actual fun clear(index: Int) {
    }

    actual fun or(another: BitSet) {
    }

    actual fun clear() {
    }
}

actual open class SQLException actual constructor(message: String?, cause: Throwable?): Exception(message, cause) {
    actual constructor(): this(null, null)
    actual constructor(message: String?): this(message, null)
    actual constructor(cause: Throwable?): this(null, cause)
}
