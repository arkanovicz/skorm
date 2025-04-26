package com.republicate.skorm

actual fun Any.callGenericGetter(key: String): Any? {
    val obj = this
    return js("obj[key]")
}
