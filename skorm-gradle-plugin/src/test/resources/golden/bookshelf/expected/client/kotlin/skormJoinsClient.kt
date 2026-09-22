package com.republicate.skorm.bookshelf

import com.republicate.skorm.*


// join attributes declaration for client
fun ExampleDatabase.initJoins() {
    // forward foreign key attribute
    bookshelf.entity("book").instanceAttributes.nullableRowAttribute<ExampleDatabase.BookshelfSchema.Dude>("donor", setOf("donor"), ExampleDatabase.BookshelfSchema.Dude)
    // forward foreign key attribute
    bookshelf.entity("book").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Author>("author", setOf("author_id"), ExampleDatabase.BookshelfSchema.Author)
    // reverse foreign key attribute
    bookshelf.entity("author").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", setOf("author_id"), ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Book>("book", setOf("book_id"), ExampleDatabase.BookshelfSchema.Book)
    // forward foreign key attribute
    bookshelf.entity("borrowing").instanceAttributes.rowAttribute<ExampleDatabase.BookshelfSchema.Dude>("dude", setOf("dude_id"), ExampleDatabase.BookshelfSchema.Dude)
    // left to right n-n join attribute
    bookshelf.entity("book").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Tag>("tags", setOf("book_id"), ExampleDatabase.BookshelfSchema.Tag)
    // right to left n-n join attribute
    bookshelf.entity("tag").instanceAttributes.rowSetAttribute<ExampleDatabase.BookshelfSchema.Book>("books", setOf("tag_id"), ExampleDatabase.BookshelfSchema.Book)
}
