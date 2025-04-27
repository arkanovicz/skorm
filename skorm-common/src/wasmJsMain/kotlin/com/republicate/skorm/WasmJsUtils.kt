package com.republicate.skorm

/*
@JsFun("eval(code)")
external fun eval(code: String): dynamic

//actual fun Any.callGenericGetter(key: String) = eval("this[key]") as Any?

actual fun Any.callGenericGetter(key: String): Any? {
    val obj = this
    val jsObj = obj.asDynamic()
    return eval("(function(o, k) { return o[k]; })")(jsObj, key)
}
*/


fun getProperty(obj: JsAny, prop: String): JsAny? = js("obj[prop]")

actual fun Any.callGenericGetter(key: String): Any? = getProperty(this as JsAny, key) as Any?
