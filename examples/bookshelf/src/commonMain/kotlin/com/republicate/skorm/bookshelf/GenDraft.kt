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

//val connector: Connector by lazy {
//    JdbcConnector("", "", "")
//}

val dummyProcessor = object: Processor {
    override fun connect(connector: Connector) {
        TODO("Not yet implemented")
    }

    override fun insert(instance: Instance): GeneratedKey? {
        TODO("Not yet implemented")
    }

    override fun update(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun upsert(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun delete(instance: Instance) {
        TODO("Not yet implemented")
    }

    override fun eval(path: String, vararg params: Any?): Instance? {
        TODO("Not yet implemented")
    }

    override fun retrieve(path: String, result: Entity?, vararg params: Any?): Instance? {
        TODO("Not yet implemented")
    }

    override fun query(path: String, result: Entity?, vararg params: Any?): RowSet {
        TODO("Not yet implemented")
    }

    override fun perform(path: String, vararg params: Any?): Int {
        TODO("Not yet implemented")
    }

    override fun attempt(path: String, vararg params: Any?): List<Int> {
        TODO("Not yet implemented")
    }

    override fun begin() {
        TODO("Not yet implemented")
    }

    override fun savePoint(name: String) {
        TODO("Not yet implemented")
    }

    override fun rollback(savePoint: String?) {
        TODO("Not yet implemented")
    }

    override fun commit() {
        TODO("Not yet implemented")
    }

}

val ExampleDatabase = Database("example", dummyProcessor)
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
