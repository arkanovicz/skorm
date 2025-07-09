package com.republicate.skorm

//open class Field(val name: String, val sqlName: String, val sqlType: String, val primary: Boolean = false, val generated: Boolean = false) {
//}

open class Field(val name: String, val type: String, val isPrimary: Boolean = false, val isGenerated: Boolean = false) {
}
