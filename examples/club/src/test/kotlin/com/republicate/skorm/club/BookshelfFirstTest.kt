package com.republicate.skorm.club

import com.republicate.skorm.bookshelf.*
import com.republicate.skorm.core.CoreProcessor
import com.republicate.skorm.jdbc.JdbcConnector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** An app that brought the bookshelf up first: the club adopts its database and creates only its own schema. */
class BookshelfFirstTest {

    @Test
    fun clubReusesTheBookshelf() = runBlocking {
        val processor = CoreProcessor(JdbcConnector())
        val bookshelf = MutableExampleDatabase(processor)
        bookshelf.configure(h2("bookshelf_first"))
        bookshelf.initialize()
        assertTrue(bookshelf.createMissingSchemas())
        val author = MutableAuthor.new().apply { name = "Italo Calvino"; insert() }
        MutableBook.new().apply { title = "Invisible Cities"; authorId = author.authorId; genre = Genre.novel; insert() }

        // the processor carries the configuration already
        val club = MutableClubDatabase(processor)
        club.initialize()
        assertSame(bookshelf, club.example)
        club.registerTables()
        assertTrue(club.createMissingSchemas())
        assertEquals(ALL_TABLES, club.tables())
        // the bookshelf's schema was not recreated
        assertEquals(listOf("Invisible Cities"), Book.browse().map { it.title }.toList())
        println("bookshelf first: club created its schema, the bookshelf kept ${Book.browse().map { it.title }.toList()}")
    }
}
