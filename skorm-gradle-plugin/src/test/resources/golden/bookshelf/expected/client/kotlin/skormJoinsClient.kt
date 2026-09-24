package com.republicate.skorm.bookshelf

import com.republicate.skorm.*


// navigations declaration for client: the read-only database's, then the mutable one's when it exists
fun ExampleDatabase.initJoins() {
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
}
