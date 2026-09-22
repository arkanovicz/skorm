package com.republicate.skorm.bookshelf


// forward foreign key
suspend fun ExampleDatabase.BookshelfSchema.Book.donor(): ExampleDatabase.BookshelfSchema.Dude? = entity.retrieve("donor", this)
// forward foreign key
suspend fun ExampleDatabase.BookshelfSchema.Book.author(): ExampleDatabase.BookshelfSchema.Author = entity.retrieve("author", this)
// reverse foreign key
suspend fun ExampleDatabase.BookshelfSchema.Author.books(): Sequence<ExampleDatabase.BookshelfSchema.Book> = entity.query("books", this)
// forward foreign key
suspend fun ExampleDatabase.BookshelfSchema.Borrowing.book(): ExampleDatabase.BookshelfSchema.Book = entity.retrieve("book", this)
// forward foreign key
suspend fun ExampleDatabase.BookshelfSchema.Borrowing.dude(): ExampleDatabase.BookshelfSchema.Dude = entity.retrieve("dude", this)
// left to right n-n join
suspend fun ExampleDatabase.BookshelfSchema.Book.tags(): Sequence<ExampleDatabase.BookshelfSchema.Tag> = entity.query("tags", this)
// right to left n-n join
suspend fun ExampleDatabase.BookshelfSchema.Tag.books(): Sequence<ExampleDatabase.BookshelfSchema.Book> = entity.query("books", this)
