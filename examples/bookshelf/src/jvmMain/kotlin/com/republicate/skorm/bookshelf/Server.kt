package com.republicate.skorm.bookshelf

import com.republicate.skorm.bookshelf.ExampleDatabase.BookshelfSchema.Book.Companion.new
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.ul
import mu.KotlinLogging

private val logger = KotlinLogging.logger { "server" }

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    configureDatabase()
    configureRouting()
    val skormConfig = environment.config.config("skorm")

}

fun Application.configureDatabase() {
//    ExampleDatabase.configure(environment.config.config("skorm"))
//    ExampleDatabase.initialize()
}

typealias Book = ExampleDatabase.BookshelfSchema.Book

fun Application.configureRouting() {
    routing {
        static {
            resources("web")
        }

        get("/") {
            call.respondRedirect("/index.html")
        }

        get("/index.html") {
            call.respondHtml {
                body {
                    h1 { +"My Bookshelf" }
                    ul {
                        runBlocking {
                            for (book in Book) {
                                li { +"book ${book.title}" }
                            }
                        }
                    }
                }
            }

        }

        route("/api") {
            route("sf") {
                get("sfq") {

                }
            }
            //rest("book")
        }
    }

    displayRoutes()
}

fun Application.displayRoutes() {
//    val root = feature(Routing)
//    val allRoutes = listOf(root) + root.children.flatMap { allRoutes(it) }
//    val allRoutesWithMethod = allRoutes.filter { it.selector is HttpMethodRouteSelector }
//    allRoutesWithMethod.forEach {
//        logger.info("route: $it")
//    }
}
