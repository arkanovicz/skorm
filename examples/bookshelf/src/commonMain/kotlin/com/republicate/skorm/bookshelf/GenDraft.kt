package com.republicate.skorm.bookshelf

import com.republicate.skorm.*

// gen - NOPE - could be several databases
//val Database.bookshelf
//  get() = getSchema("bookshelf")

// without code generation
//val db = Database("example", ApiEndPoint())
//val bookshelf = db.getSchema("bookshelf")

// WITH code generation
// server:

class BookshelfShema(db: Database) : Schema("bookshelf", db) {  }
val connector: Connector by lazy {
    JdbcConnector("", "", "")
}

val ExampleDatabase = Database("example")
val BookshelfSchema: Schema by lazy { ExampleDatabase.getSchema("bookshelf") } // alternative: use db.get("schema")
val BookEntity: Entity by lazy {
    BookshelfSchema.getEntity("book").apply {
        factory = { Book() }
    }
}

class Book(): Instance(BookEntity)
{
    companion object {
        operator fun get(id: Long) = BookEntity.fetch(id)
        operator fun iterator(): Iterator<Book> = object : Iterator<Book> {
            private val it = BookEntity.iterator()
            override fun next() = it.next() as Book
            override fun hasNext() = it.hasNext()
        }
    }
    var title
        get() = getString("title")
        set(newTitle: String?) { put("title", newTitle) }
}
