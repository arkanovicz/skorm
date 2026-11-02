@file:OptIn(kotlin.time.ExperimentalTime::class) // Instant is experimental below Kotlin 2.3

package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import kotlin.time.Instant
import com.republicate.kson.Json

open class CurrentBorrower(): ExampleDatabase.BookshelfSchema.DudeImpl() {
    val borrowingDate: LocalDate
        get() = getLocalDate("borrowingDate")!!
}

open class Catalog(): Json.MutableObject() {
    val title: String
        get() = getString("title")!!
    val borrowings: Int
        get() = getInt("borrowings")!!
}

open class LastBorrowing(): Json.MutableObject() {
    val borrowingDate: LocalDate
        get() = getLocalDate("borrowingDate")!!
    val restitutionDate: LocalDate?
        get() = getLocalDate("restitutionDate")
}

open class Stats(): Json.MutableObject() {
    val titleLength: Int
        get() = getInt("titleLength")!!
    val borrowed: Int
        get() = getInt("borrowed")!!
}

