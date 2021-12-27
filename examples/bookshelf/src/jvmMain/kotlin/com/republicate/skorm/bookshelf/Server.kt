package com.republicate.skorm.bookshelf

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
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

fun Application.configureDatabae() {
    ExampleDatabase.configure(environment.config.config("skorm"))
    ExampleDatabase.initialize()z""
}

fun Application.configureRouting() {
    routing {
        static {
            resources("web")
        }

        get("/") {
            call.redirect("/index.html")
        }

        get("/index.html") {
            call.respondHtml {
                body {
                    h1 { +"My Bookshelf" }
                    ul {
                        for (book in Book) {
                            li { +"book ${book.title}" }
                        }
                    }
                }
            }

        }

        route("/api") {
            //rest("book")
        }
    }

    displayRoutes()
}

fun Application.displayRoutes() {
    val root = feature(Routing)
    val allRoutes = listOf(root) + root.children.flatMap { allRoutes(it) }
    val allRoutesWithMethod = allRoutes.filter { it.selector is HttpMethodRouteSelector }
    allRoutesWithMethod.forEach {
        logger.info("route: $it")
    }
}
