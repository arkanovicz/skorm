package com.republicate.skorm.bookshelf

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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            }
        }
    }
}
