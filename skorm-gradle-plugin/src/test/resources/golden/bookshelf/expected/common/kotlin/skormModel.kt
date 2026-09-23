package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import com.republicate.kson.Json

open class CurrentBorrower(): ExampleDatabase.BookshelfSchema.Dude() {
    val borrowingDate: LocalDate
        get() = getLocalDate("borrowingDate")!!
}

open class Catalog(): Json.MutableObject() {
    val title: String
        get() = getString("title")!!
    val borrowings: Int
        get() = getInt("borrowings")!!
}

open class Stats(): Json.MutableObject() {
    val titleLength: Int
        get() = getInt("titleLength")!!
    val borrowed: Int
        get() = getInt("borrowed")!!
}

