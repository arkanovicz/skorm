package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import com.republicate.skorm.*
import com.republicate.kson.Json


// runtime model attributes declaration for client: the read-only database's, then the mutable one's when it exists
fun com.republicate.skorm.bookshelf.ExampleDatabase.initRuntimeModel() {

    // attribute bookshelf.booksCount
    ExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", setOf())

    // attribute Book.currentBorrower
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", setOf("book_id"), ::CurrentBorrower)

    // attribute Author.countInGenre
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.scalarAttribute<Int>("countInGenre", setOf("author_id","genre"))

    // attribute Author.catalog
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<Catalog>("catalog", setOf("author_id"), ::Catalog)

    // attribute Book.stats
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<Stats>("stats", setOf("book_id"), ::Stats)
    if (!MutableExampleDatabase.isInstantiated()) return

    // attribute bookshelf.booksCount
    MutableExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", setOf())

    // attribute Book.currentBorrower
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", setOf("book_id"), ::CurrentBorrower)

    // attribute Book.lend
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("lend", setOf("dude_id","book_id"))

    // attribute Book.restitute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("restitute", setOf("book_id"))

    // attribute Book.returnFrom
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("returnFrom", setOf("returned_on","book_id","dude_id"))

    // attribute bookshelf.newBookBy
    MutableExampleDatabase.bookshelf.mutationAttribute("newBookBy", setOf("author_name","title"))

    // attribute Author.countInGenre
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.scalarAttribute<Int>("countInGenre", setOf("author_id","genre"))

    // attribute Author.catalog
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<Catalog>("catalog", setOf("author_id"), ::Catalog)

    // attribute Book.stats
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<Stats>("stats", setOf("book_id"), ::Stats)
}
