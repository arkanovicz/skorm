package com.republicate.skorm

open class ConcreteField(val sqlName: String, val sqlType: String, name: String, primary: Boolean = false, generated: Boolean = false): Field(name, primary, generated)

