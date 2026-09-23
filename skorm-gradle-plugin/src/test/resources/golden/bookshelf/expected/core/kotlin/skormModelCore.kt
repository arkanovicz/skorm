package com.republicate.skorm.bookshelf

import kotlinx.datetime.*
import com.republicate.skorm.core.*
import com.republicate.kson.Json


// runtime model attributes declaration for core
fun com.republicate.skorm.bookshelf.ExampleDatabase.initRuntimeModel() {

    // attribute bookshelf.booksCount
    ExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", """
        SELECT count(*) FROM book;""".trimIndent())


    // attribute Book.currentBorrower
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", """
        SELECT dude.*, borrowing_date FROM bookshelf.borrowing
        JOIN dude USING (dude_id)
        WHERE book_id = {book_id}
        AND restitution_date IS NULL;""".trimIndent(), ::CurrentBorrower)


    // attribute Book.lend
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("lend", """
        INSERT INTO borrowing (dude_id, book_id, borrowing_date)
        VALUES ({dude_id}, {book_id}, now());""".trimIndent())


    // attribute Book.restitute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("restitute", """
        UPDATE borrowing SET restitution_date = NOW()
        WHERE book_id = {book_id} AND restitution_date IS NULL;""".trimIndent())


    // attribute Book.returnFrom
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("returnFrom", """
        UPDATE bookshelf.borrowing SET restitution_date = {returned_on}
        WHERE book_id = {book_id} AND dude_id = {dude_id} AND restitution_date IS NULL;""".trimIndent())


    // attribute Author.countInGenre
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.scalarAttribute<Int>("countInGenre", """
        SELECT count(*) FROM bookshelf.book
        WHERE author_id = {author_id} AND genre = {genre};""".trimIndent())


    // attribute Author.catalog
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<Catalog>("catalog", """
        SELECT book.title, COUNT(borrowing.book_id) borrowings
        FROM bookshelf.book
        LEFT JOIN bookshelf.borrowing USING (book_id)
        WHERE book.author_id = {author_id}
        GROUP BY book.book_id, book.title;""".trimIndent(), ::Catalog)


    // attribute Book.stats
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<Stats>("stats", """
        SELECT
             CHARACTER_LENGTH(title) title_length,
             ( SELECT COUNT(*) FROM borrowing WHERE book_id = {book_id} ) borrowed
        FROM book
        WHERE book_id = {book_id};""".trimIndent(), ::Stats)

}
