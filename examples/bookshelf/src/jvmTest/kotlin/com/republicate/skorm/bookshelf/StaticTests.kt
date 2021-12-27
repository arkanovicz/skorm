package com.republicate.skorm.bookshelf

import io.ktor.http.*
import io.ktor.server.testing.*
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StaticTests {
    @Test
    fun testIndex() {
        withTestApplication({
            configureRouting()
        }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val doc = Jsoup.parse(response.content)
                val title = doc.select("h1")
                assertEquals("Welcome to Skorm Bookshelf Example!", title.text())
            }
        }
    }
}
