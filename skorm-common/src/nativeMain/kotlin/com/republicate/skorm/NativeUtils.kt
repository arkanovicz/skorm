package com.republicate.skorm

actual typealias BitSet = kotlin.native.BitSet

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
