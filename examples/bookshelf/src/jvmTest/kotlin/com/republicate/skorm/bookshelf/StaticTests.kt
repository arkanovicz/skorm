package com.republicate.skorm.bookshelf

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.*
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        }
    }
}
