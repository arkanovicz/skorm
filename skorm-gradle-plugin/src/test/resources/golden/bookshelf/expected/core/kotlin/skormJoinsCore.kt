package com.republicate.skorm.bookshelf

import com.republicate.skorm.core.*


// navigations declaration for core: the read-only database's, then the mutable one's when it exists
fun ExampleDatabase.initJoins() {
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
}
