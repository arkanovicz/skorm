package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import com.republicate.skorm.*
import com.republicate.kson.Json


// runtime model attributes declaration for client
fun com.republicate.skorm.bookshelf.ExampleDatabase.initRuntimeModel() {

    // attribute bookshelf.booksCount
    ExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", setOf())

    // attribute Book.currentBorrower
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", setOf("book_id"), ::CurrentBorrower)

    // attribute Book.lend
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("lend", setOf("dude_id","book_id"))

    // attribute Book.restitute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("restitute", setOf("book_id"))

    // attribute Book.returnFrom
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("returnFrom", setOf("returned_on","book_id","dude_id"))

    // attribute bookshelf.newBookBy
    ExampleDatabase.bookshelf.mutationAttribute("newBookBy", setOf("author_name","title"))

    // attribute Author.countInGenre
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.scalarAttribute<Int>("countInGenre", setOf("author_id","genre"))

    // attribute Author.catalog
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<Catalog>("catalog", setOf("author_id"), ::Catalog)

    // attribute Book.stats
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<Stats>("stats", setOf("book_id"), ::Stats)
}
