package com.republicate.skorm.bookshelf

import com.republicate.skorm.*
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for client, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun ExampleDatabase.registerAttributes() {
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<ExampleDatabase.BookshelfSchema.Dude>("donor", setOf("donor"), ExampleDatabase.BookshelfSchema.Dude)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Author>("author", setOf("author_id"), ExampleDatabase.BookshelfSchema.Author)
    // reverse foreign key attribute
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", setOf("author_id"), ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Book>("book", setOf("book_id"), ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Dude>("dude", setOf("dude_id"), ExampleDatabase.BookshelfSchema.Dude)
    // left to right n-n join attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Tag>("tags", setOf("book_id"), ExampleDatabase.BookshelfSchema.Tag)
    // right to left n-n join attribute
    ExampleDatabase.bookshelf.entity("tag").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", setOf("tag_id"), ExampleDatabase.BookshelfSchema.Book)

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
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableDude>("donor", setOf("donor"), MutableExampleDatabase.MutableBookshelfSchema.MutableDude)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableAuthor>("author", setOf("author_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableAuthor)
    // reverse foreign key attribute
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("books", setOf("author_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableBook)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("book", setOf("book_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableBook)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableDude>("dude", setOf("dude_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableDude)
    // left to right n-n join attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableTag>("tags", setOf("book_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableTag)
    // right to left n-n join attribute
    MutableExampleDatabase.bookshelf.entity("tag").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("books", setOf("tag_id"), MutableExampleDatabase.MutableBookshelfSchema.MutableBook)

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
