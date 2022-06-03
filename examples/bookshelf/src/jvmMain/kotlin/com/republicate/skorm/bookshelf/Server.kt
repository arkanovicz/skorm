package com.republicate.skorm.bookshelf

import com.republicate.kson.toJsonObject
import com.republicate.skorm.*
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

fun ApplicationConfig.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().sorted().forEach { key ->
        val elems = key.split('.')
        // elems[n+1] determines the nature (list or map) of container at elems[n]
        var target: Any = map
        for (i in 0..elems.size - 2) {
            val elem = elems[i]
            val next = elems[i + 1]
            target = when (val nextIndex = next.toIntOrNull()) {
                null -> {
                    when (target) {
                        is Map<*,*> ->
                            (target as MutableMap<String, Any>).getOrPut(elem) {
                                mutableMapOf<String, Any>()
                            }.also {
                                if (it !is Map<*,*>) throw SkormException("expecting a map")
                            }
                        is List<*> ->
                            if (next != "size" || i != elems.size - 2) throw SkormException("expecting list size")
                            else if (tryGetString(key)?.toIntOrNull() == target.size) throw SkormException("list size inconsistency")
                            else continue // ignoring regular "size" list property
                        else -> throw SkormException("unhandled case")
                    }
                }
                0 -> {
                    when (target) {
                        is Map<*,*> ->
                            mutableListOf<Any>().also {
                                (target as MutableMap<String, Any>).put(elem, it)?.also {
                                    throw SkormException("expecting an empty slot")
                                }
                            }
                        is List<*> ->
                            mutableListOf<Any>().also {
                                (target as MutableList<Any>).apply {
                                    if (size != nextIndex) throw SkormException("wrong list size")
                                    add(it)
                                }
                            }
                        else -> throw SkormException("unhandled case")
                    }
                }
                else -> {
                    when (target) {
                        is Map<*,*> ->
                            target.get(elem)?.also {
                                if (it !is List<*>) throw SkormException("epecting a list")
                                if (it.size != nextIndex) throw SkormException("wrong list size")
                            } ?: throw SkormException("expecting a list")
                        is List<*> ->
                            target.lastOrNull()?.also {
                                if (it !is List<*>) throw SkormException("epecting a list")
                                if (it.size != nextIndex) throw SkormException("wrong list size")
                            } ?: throw SkormException("expecting a list")
                        else -> throw SkormException("unhandled case")
                    }
                }
            }
        }
        val value = property(key).getString()
        when (target) {
            is Map<*,*> -> (target as MutableMap<String, Any>).put(elems.last(), value)?.also {
                throw SkormException("expecting empty slot")
            }
            is List<*> -> (target as MutableList<Any>).add(value)
            else -> throw SkormException("unhandled case")
        }
    }
    return map
}

val exampleDatabase = ExampleDatabase(CoreProcessor(JdbcProvider()))

fun Application.configureDatabase() {

    println("Configuring...")
    environment.config.config("skorm").apply {
        exampleDatabase.configure(toMap().toJsonObject())
    }
    exampleDatabase.initialize()
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
