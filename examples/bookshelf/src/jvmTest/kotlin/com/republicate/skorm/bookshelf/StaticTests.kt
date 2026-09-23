package com.republicate.skorm.bookshelf

import com.republicate.skorm.SkormException
import com.republicate.skorm.transaction
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A non-suspend view of a row delegates the whole field contract. */
private class BookView(row: Book) : BookFields by row

class StaticTests {
    @Test
    fun testIndex() {
        testApplication {
            environment {
                config = ApplicationConfig("application.conf")
                println("@@@@@ CONFIG: ")
                println(config)
            }
            val response = client.get("/")
            val doc = Jsoup.parse(response.bodyAsText())
            println(doc)
            assertEquals(HttpStatusCode.OK, response.status)
            val title = doc.select("h1")
            assertEquals("My Bookshelf", title.text())
            val book = doc.select("li")
            assertTrue(book.text().startsWith("Le Language des Pierres"))

            // Generated-attribute runtime checks (single app boot — the database singleton
            // can only be initialized once, so these share testIndex's application).
            runBlocking {
                val theBook = Book.browse().first()
                val alice = Dude.browse().first { it.name == "Alice" }

                assertNull(theBook.currentBorrower())
                theBook.lend(alice.dudeId.toLong())

                // entity-composite attr returns the generated subclass, not the base entity
                val borrower = theBook.currentBorrower()
                assertNotNull(borrower)
                assertEquals("Alice", borrower.name)
                assertNotNull(borrower.borrowingDate)

                // multi-param mut binds {dude_id}/{returned_on} by name despite reversed order
                val changed = theBook.returnFrom(alice.dudeId.toLong(), LocalDate(2026, 6, 10))
                assertEquals(1L, changed)
                assertNull(theBook.currentBorrower())

                // enum field round-trips, and an enum-typed query param filters correctly
                val author = Author.browse().first()
                assertEquals(Genre.essay, theBook.genre)
                assertEquals(1, author.countInGenre(Genre.essay))
                assertEquals(0, author.countInGenre(Genre.poetry))

                // a nullable forward FK yields null rather than failing the cast
                assertNull(theBook.donor())
                assertNotNull(theBook.author())

                // traversal intent: `book *-- author` exposes the collection, the chevron in
                // `donor --> dude?` withholds it — neither the accessor nor its registration exists
                assertEquals(listOf(theBook.title), author.books().map { it.title }.toList())
                assertEquals(
                    listOf("Author", "Tag"),
                    listOf(Author::class, Dude::class, Tag::class)
                        .filter { c -> c.java.methods.any { it.name == "books" } }.map { it.simpleName }
                )
                assertThrows<SkormException> {
                    ExampleDatabase.bookshelf.entity("dude").instanceAttributes.findAttribute<Any>("books")
                }

                // many-to-many, both ways: the accessor name, the query direction and the
                // row type have to agree between the accessor and its registration
                assertEquals(listOf("go", "stones"), theBook.tags().map { it.label }.toList().sorted())
                val go = Tag.browse().first { it.label == "go" }
                assertEquals(listOf(theBook.title), go.books().map { it.title }.toList())

                // a multi-statement mutation runs as one transaction: the second statement sees the first,
                // and the result counts the rows changed by both
                assertEquals(2L, ExampleDatabase.bookshelf.newBookBy("Ursula K. Le Guin", "A Wizard of Earthsea"))
                val ursula = Author.browse().first { it.name == "Ursula K. Le Guin" }
                assertEquals(listOf("A Wizard of Earthsea"), ursula.books().map { it.title }.toList())

                // blocking twins: the same name for a reflection-driven caller, a List rather than a Sequence
                assertEquals(theBook.tags().map { it.label }.toList(), theBook.tagsBlocking().map { it.label })
                assertEquals(2, ExampleDatabase.bookshelf.booksCountBlocking())
                assertTrue(Book::class.java.methods.any { it.name == "tags" && it.parameterCount == 0 })
                // and a twin called while a transaction runs uses that transaction: it sees the uncommitted row
                exampleDatabase.transaction {
                    val tag = Tag().apply { label = "uncommitted"; insert() }
                    BookTag().apply { bookId = theBook.bookId; tagId = tag.tagId; insert() }
                    assertTrue("uncommitted" in theBook.tagsBlocking().map { it.label })
                }

                // the generated field interface is delegable, and the delegate's getters
                // are real methods — what a reflection-driven template engine needs
                val view = BookView(theBook)
                assertEquals(theBook.title, view.title)
                assertEquals(Genre.essay, view.genre)
                assertNull(view.donor)
                assertEquals(
                    listOf("getDonor", "getGenre", "getTitle"),
                    BookView::class.java.methods.map { it.name }
                        .filter { it in setOf("getTitle", "getGenre", "getDonor") }.sorted()
                )
            }
        }
    }
}
