package com.republicate.skorm

actual open class SQLException actual constructor(message: String?, cause: Throwable?): Exception(message, cause) {
    actual constructor(): this(null, null)
    actual constructor(message: String?): this(message, null)
    actual constructor(cause: Throwable?): this(null, cause)
}
