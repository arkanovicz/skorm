package com.republicate.skorm.bookshelf

import com.republicate.kson.Json

// this is a hack, while waiting for the next release of essential-kson
fun Json.Object.getInt(key: String) = getInteger(key)
