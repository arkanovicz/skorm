package com.republicate.skorm.bookshelf

import com.republicate.skorm.CoreProcessor
import com.republicate.skorm.jdbc.JdbcProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { "server" }

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    install(StatusPages) {
        exception<Throwable> { call: ApplicationCall, cause ->
            logger.error(cause) {
                "unable to render page"
            }
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    configureDatabase()
    configureRouting()
}

fun ApplicationConfig.toMap(): Map<String, Any?> =
    keys().map { key ->
        key to config(key).toString()
    }.toMap()

lateinit var exampleDatabase: ExampleDatabase

fun Application.configureDatabase() {

    environment.config.config("skorm").apply {
        val url = property("jdbc.url").getString()
        println("@@@@ url = $url")
        val jdbc = JdbcProvider(url)

        exampleDatabase = ExampleDatabase(CoreProcessor(jdbc))

        // create and populate our example db
        // exampleDatabase.populate()

        keys().forEach { key ->
            val value = property(key)
            println("$key = $value")
        }
//    ExampleDatabase.configure(environment.config.config("skorm"))
//    ExampleDatabase.initialize()
    }
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
                println("responding html")
                head {
                    script {
                        src = "index.js"
                    }
                }
                body {
                    h1 { +"My Bookshelf" }
                    ul {
                        println("inside ul")
                        runBlocking {
//                            for (n in 1..10) {
//                                li { +"$n" }
//                            }
                            println("before loop")
                            for (book in Book) {
                                println("inside loop")
                                li {
                                    +"book ${book.title}"
                                    button {
                                        +"reserve"
                                        onClick = "reserve(${book.bookId})"
                                    }
                                }
                            }
                            println("after loop")
                        }
                        println("after runBlocking")
                    }
                }
                println("after body")
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
