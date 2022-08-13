package com.republicate.skorm.bookshelf

import com.republicate.kson.Json
import com.republicate.kson.toJsonObject
import com.republicate.skorm.SkormException
import com.republicate.skorm.core.CoreProcessor
import com.republicate.skorm.core.mutationAttribute
import com.republicate.skorm.jdbc.JdbcConnector
import com.republicate.skorm.rest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    install(ContentNegotiation) {
        register(ContentType.Application.Json, KsonConverter)
    }
    configureDatabase()
    configureRouting()
}

// white waiting for ktor 2.0
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

val exampleDatabase = ExampleDatabase(CoreProcessor(JdbcConnector()))

const val CREATION_SCRIPT = "create-script.sql"

fun Application.configureDatabase() {

    println("Configuring...")
    environment.config.config("skorm").apply {
        exampleDatabase.configure(toMap().toJsonObject())
    }
    exampleDatabase.initialize()
    exampleDatabase.initJoins()

    println("Creating database...")
    val creationScript = Application::class.java.getResource("/${CREATION_SCRIPT}").readText()
    exampleDatabase.mutationAttribute("create", creationScript)
    runBlocking {
        exampleDatabase.perform("create") // or execute attribute directly...

        // test data
        val author = Author().apply {
          name = "Motoki Noguchi"
          insert()
        }
        val book = Book().apply {
            title = "Le Language des Pierres"
            authorId = author.authorId
            insert()
        }
    }
}

typealias Author = ExampleDatabase.BookshelfSchema.Author
typealias Book = ExampleDatabase.BookshelfSchema.Book

fun Application.configureRouting() {
    routing {
        // TODO - web package
        // static {
        //     resources("web")
        // }
        static("/static") {
            resources()
        }

        get("/") {
            call.respondRedirect("/index.html")
        }

        get("/index.html") {
            call.respondHtml {
                println("responding html")
                head {
                    script {
                        src = "/static/bookshelf.js"
                    }
                }
                body {
                    h1 { +"My Bookshelf" }
                    ul {
                        runBlocking {
                            for (book in Book) {
                                val authorName = book.author().name
                                li {
                                    +book.title
                                    i { +" by " }
                                    +authorName
                                    br()
                                    button(type=ButtonType.submit, classes = "reserve") {
                                        attributes["data-book_id"] = "${book.bookId}"
                                        +"reserve"
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        route("/api/example") {
            rest(ExampleDatabase.bookshelf)
        }
    }

//    displayRoutes()
}

//fun Application.displayRoutes() {
//    val root = feature(Routing)
//    val allRoutes = listOf(root) + root.children.flatMap { allRoutes(it) }
//    val allRoutesWithMethod = allRoutes.filter { it.selector is HttpMethodRouteSelector }
//    allRoutesWithMethod.forEach {
//        logger.info("route: $it")
//    }
//}

// ktor essential-kson converter ; here for now, but should move elsewhere

object KsonConverter: ContentConverter {
    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val input = object: Json.Input {
            val reader = content.toInputStream().reader(charset)
            override fun read() = reader.read().toChar()
        }
        return Json.parseValue(input)
    }
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent? = TextContent(value.toString(), contentType.withCharsetIfNeeded(charset))
}
