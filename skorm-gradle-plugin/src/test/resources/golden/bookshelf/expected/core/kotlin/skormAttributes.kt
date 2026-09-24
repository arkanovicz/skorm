package com.republicate.skorm.bookshelf

import com.republicate.skorm.core.*
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for core, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun ExampleDatabase.registerAttributes() {
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<ExampleDatabase.BookshelfSchema.Dude>("donor", "SELECT * FROM bookshelf.dude WHERE dude.dude_id = {donor};", ExampleDatabase.BookshelfSchema.Dude)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Author>("author", "SELECT * FROM bookshelf.author WHERE author.author_id = {author_id};", ExampleDatabase.BookshelfSchema.Author)
    // reverse foreign key attribute
    ExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", "SELECT * FROM bookshelf.book WHERE book.author_id = {author_id};", ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Book>("book", "SELECT * FROM bookshelf.book WHERE book.book_id = {book_id};", ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    ExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Dude>("dude", "SELECT * FROM bookshelf.dude WHERE dude.dude_id = {dude_id};", ExampleDatabase.BookshelfSchema.Dude)
    // left to right n-n join attribute
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Tag>("tags", "SELECT towards_table.* FROM bookshelf.book_tag AS join_table JOIN bookshelf.tag AS towards_table ON towards_table.tag_id = join_table.tag_id WHERE join_table.book_id = {book_id};", ExampleDatabase.BookshelfSchema.Tag)
    // right to left n-n join attribute
    ExampleDatabase.bookshelf.entity("tag").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", "SELECT towards_table.* FROM bookshelf.book_tag AS join_table JOIN bookshelf.book AS towards_table ON towards_table.book_id = join_table.book_id WHERE join_table.tag_id = {tag_id};", ExampleDatabase.BookshelfSchema.Book)

    // attribute bookshelf.booksCount
    ExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", """
        SELECT count(*) FROM book;""".trimIndent())

    // attribute Book.currentBorrower
    ExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", """
        SELECT dude.*, borrowing_date FROM bookshelf.borrowing
        JOIN dude USING (dude_id)
        WHERE book_id = {book_id}
        AND restitution_date IS NULL;""".trimIndent(), ::CurrentBorrower)

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
    if (!MutableExampleDatabase.isInstantiated()) return
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableDude>("donor", "SELECT * FROM bookshelf.dude WHERE dude.dude_id = {donor};", MutableExampleDatabase.MutableBookshelfSchema.MutableDude)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableAuthor>("author", "SELECT * FROM bookshelf.author WHERE author.author_id = {author_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableAuthor)
    // reverse foreign key attribute
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("books", "SELECT * FROM bookshelf.book WHERE book.author_id = {author_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableBook)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("book", "SELECT * FROM bookshelf.book WHERE book.book_id = {book_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableBook)
    // forward foreign key attribute
    MutableExampleDatabase.bookshelf.entity("borrowing").instanceAttributes.rowAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableDude>("dude", "SELECT * FROM bookshelf.dude WHERE dude.dude_id = {dude_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableDude)
    // left to right n-n join attribute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableTag>("tags", "SELECT towards_table.* FROM bookshelf.book_tag AS join_table JOIN bookshelf.tag AS towards_table ON towards_table.tag_id = join_table.tag_id WHERE join_table.book_id = {book_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableTag)
    // right to left n-n join attribute
    MutableExampleDatabase.bookshelf.entity("tag").instanceAttributes.rowSetAttribute<MutableExampleDatabase.MutableBookshelfSchema.MutableBook>("books", "SELECT towards_table.* FROM bookshelf.book_tag AS join_table JOIN bookshelf.book AS towards_table ON towards_table.book_id = join_table.book_id WHERE join_table.tag_id = {tag_id};", MutableExampleDatabase.MutableBookshelfSchema.MutableBook)

    // attribute bookshelf.booksCount
    MutableExampleDatabase.bookshelf.scalarAttribute<Int>("booksCount", """
        SELECT count(*) FROM book;""".trimIndent())

    // attribute Book.currentBorrower
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.nullableRowAttribute<CurrentBorrower>("currentBorrower", """
        SELECT dude.*, borrowing_date FROM bookshelf.borrowing
        JOIN dude USING (dude_id)
        WHERE book_id = {book_id}
        AND restitution_date IS NULL;""".trimIndent(), ::CurrentBorrower)

    // attribute Book.lend
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("lend", """
        INSERT INTO borrowing (dude_id, book_id, borrowing_date)
        VALUES ({dude_id}, {book_id}, now());""".trimIndent())

    // attribute Book.restitute
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("restitute", """
        UPDATE borrowing SET restitution_date = NOW()
        WHERE book_id = {book_id} AND restitution_date IS NULL;""".trimIndent())

    // attribute Book.returnFrom
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.mutationAttribute("returnFrom", """
        UPDATE bookshelf.borrowing SET restitution_date = {returned_on}
        WHERE book_id = {book_id} AND dude_id = {dude_id} AND restitution_date IS NULL;""".trimIndent())

    // attribute bookshelf.newBookBy
    MutableExampleDatabase.bookshelf.mutationAttribute("newBookBy", """
        INSERT INTO author (name) VALUES ({author_name});
INSERT INTO book (title, author_id, genre)
        SELECT {title}, author_id, 'novel' FROM author WHERE name = {author_name};""".trimIndent())

    // attribute Author.countInGenre
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.scalarAttribute<Int>("countInGenre", """
        SELECT count(*) FROM bookshelf.book
        WHERE author_id = {author_id} AND genre = {genre};""".trimIndent())

    // attribute Author.catalog
    MutableExampleDatabase.bookshelf.entity("author").instanceAttributes.rowSetAttribute<Catalog>("catalog", """
        SELECT book.title, COUNT(borrowing.book_id) borrowings
        FROM bookshelf.book
        LEFT JOIN bookshelf.borrowing USING (book_id)
        WHERE book.author_id = {author_id}
        GROUP BY book.book_id, book.title;""".trimIndent(), ::Catalog)

    // attribute Book.stats
    MutableExampleDatabase.bookshelf.entity("book").instanceAttributes.rowAttribute<Stats>("stats", """
        SELECT
             CHARACTER_LENGTH(title) title_length,
             ( SELECT COUNT(*) FROM borrowing WHERE book_id = {book_id} ) borrowed
        FROM book
        WHERE book_id = {book_id};""".trimIndent(), ::Stats)
}
