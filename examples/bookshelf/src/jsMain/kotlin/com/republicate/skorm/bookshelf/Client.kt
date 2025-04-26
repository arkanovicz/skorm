package com.republicate.skorm.bookshelf

import com.republicate.skorm.ApiClient
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.EventListener
import org.w3c.dom.get

private val logger = KotlinLogging.logger("bookshelf")

val exampleDatabase = ExampleDatabase(ApiClient("${window.location.origin}/api"))
val bookshelf = ExampleDatabase.bookshelf

typealias Author = ExampleDatabase.BookshelfSchema.Author
typealias Book = ExampleDatabase.BookshelfSchema.Book

fun main() {
    window.onload = {
        logger.info { "window loaded" }
        exampleDatabase.initialize()
        exampleDatabase.initJoins()
        exampleDatabase.initRuntimeModel()
        logger.info { "db initialized" }

        sel(".lend-form").submit { event ->
            logger.info { "lend event" }
            event.preventDefault()
            GlobalScope.launch {
                val form = event.element()
                val bookId = form.attributes["data-book_id"]?.let { it.value } ?: throw Error("book id not found")
                val book = Book.fetch(bookId) ?: throw Error("invalid book id")
                val select = form.children["dude_id"] as HTMLSelectElement? ?: throw Error("invalid select")
                val dudeId = select.selectedOptions[0]?.attributes?.get("value")?.value?.toLong() ?: throw Error("dude id not found")
                book.lend(dudeId)
                document.location!!.reload()
            }
        }
        sel(".restitute-form").submit { event ->
            event.preventDefault()
            GlobalScope.launch {
                val form = event.element()
                val bookId = form.attributes["data-book_id"]?.let { it.value } ?: throw Error("book id not found")
                val book = Book.fetch(bookId) ?: throw Error("invalid book id")
                book.restitute()
                document.location!!.reload()
            }
        }
    }
}
