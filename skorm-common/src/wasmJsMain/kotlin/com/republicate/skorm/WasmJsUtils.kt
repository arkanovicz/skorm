package com.republicate.skorm

fun getProperty(obj: JsAny, prop: String): JsAny? = js("obj[prop]")

@Suppress("UNCHECKED_CAST")
actual fun Any.callGenericGetter(key: String): Any? = getProperty(this as JsAny, key) as Any?
