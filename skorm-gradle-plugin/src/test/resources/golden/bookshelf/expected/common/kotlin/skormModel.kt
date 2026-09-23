package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import com.republicate.kson.Json
import com.republicate.skorm.bookshelf.ExampleDatabase.BookshelfSchema.Genre




// attribute bookshelf.booksCount
suspend fun ExampleDatabase.BookshelfSchema.`booksCount`() = eval<Int>("booksCount")

open class CurrentBorrower(): ExampleDatabase.BookshelfSchema.Dude() {
    val borrowingDate: LocalDate
        get() = getLocalDate("borrowingDate")!!
}


// attribute Book.currentBorrower
suspend fun ExampleDatabase.BookshelfSchema.Book.`currentBorrower`() = retrieve<CurrentBorrower?>("currentBorrower") as CurrentBorrower?



// attribute Book.lend
suspend fun ExampleDatabase.BookshelfSchema.Book.`lend`(dude_id: Long) = perform("lend", dude_id)



// attribute Book.restitute
suspend fun ExampleDatabase.BookshelfSchema.Book.`restitute`() = perform("restitute")



// attribute Book.returnFrom
suspend fun ExampleDatabase.BookshelfSchema.Book.`returnFrom`(dude_id: Long ,returned_on: LocalDate) = perform("returnFrom", mapOf("dudeId" to dude_id, "returnedOn" to returned_on))



// attribute bookshelf.newBookBy
suspend fun ExampleDatabase.BookshelfSchema.`newBookBy`(author_name: String ,title: String) = perform("newBookBy", mapOf("authorName" to author_name, "title" to title))



// attribute Author.countInGenre
suspend fun ExampleDatabase.BookshelfSchema.Author.`countInGenre`(genre: Genre) = eval<Int>("countInGenre", genre)

open class Catalog(): Json.MutableObject() {
    val title: String
        get() = getString("title")!!
    val borrowings: Int
        get() = getInt("borrowings")!!
}


// attribute Author.catalog
suspend fun ExampleDatabase.BookshelfSchema.Author.`catalog`() = query<Catalog>("catalog")

open class Stats(): Json.MutableObject() {
    val titleLength: Int
        get() = getInt("titleLength")!!
    val borrowed: Int
        get() = getInt("borrowed")!!
}


// attribute Book.stats
suspend fun ExampleDatabase.BookshelfSchema.Book.`stats`() = retrieve<Stats>("stats") as Stats

