package com.republicate.skorm.bookshelf

import com.republicate.skorm.ApiClient
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.w3c.dom.events.EventListener
import org.w3c.dom.get

private val logger = KotlinLogging.logger("bookshelf")

val exampleDatabase = ExampleDatabase(ApiClient("${window.location.origin}/api"))
val bookshelf = ExampleDatabase.bookshelf

typealias Author = ExampleDatabase.BookshelfSchema.Author
typealias Book = ExampleDatabase.BookshelfSchema.Book

fun reserve(bookId: Int) {
    logger.info { "reserve $bookId" }
    GlobalScope.launch {
        ExampleDatabase.bookshelf.attempt("reserve", bookId)
    }
}

fun main() {
    window.onload = {
        logger.info { "window loaded" }
        exampleDatabase.initialize()
        exampleDatabase.initJoins()
        exampleDatabase.initRuntimeModel()
        logger.info { "db initialized" }
        sel(".reserve").click { event ->
            logger.info { "reserve" }
            logger.info { "target = ${event.element()}" }
            GlobalScope.launch {
                logger.info { "inside launch" }
                logger.info { "target = ${event.element()}" }
                val bookId = event.element().attributes["data-book_id"]?.let { it.value } ?: throw Error("book id not found")
                logger.info { "bookId = $bookId" }
                val book = Book.fetch(bookId) ?: throw Error("book not found")
                logger.info { "book = $book" }
                logger.info { book.title }
            }
        }
    }
}
