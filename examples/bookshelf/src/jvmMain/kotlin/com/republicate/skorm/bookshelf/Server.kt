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
import io.github.oshai.kotlinlogging.KotlinLogging

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
    install(IgnoreTrailingSlash)
    configureDatabase()
    configureRouting()
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
    exampleDatabase.initRuntimeModel()

    println("Creating database...")
    val creationScript = Application::class.java.getResource("/${CREATION_SCRIPT}").readText()
    // @@@
    //exampleDatabase.mutationAttribute("create", creationScript)

    val tst = creationScript + """
    SELECT * FROM book;;
    SELECT * FROM bookshelf.book;;
    SELECT * FROM BOOKSHELF.book;;
    """.trimIndent()

    exampleDatabase.mutationAttribute("create", tst)

    runBlocking {
        exampleDatabase.perform("create") // or execute attribute directly...

        println("@@@@@@@@@@@@@ DATABASE STRUCTURE CREATED")
        val authors = Author.browse().toList()
        assert(authors.size == 0)

        println("@@@@@@@@@@@@@ ENTITIES ARE VISIBLE")

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
        for (name in listOf("Alice", "Bob")) {
            Dude().also { it.name = name }.insert()
        }
    }
}

typealias Author = ExampleDatabase.BookshelfSchema.Author
typealias Book = ExampleDatabase.BookshelfSchema.Book
typealias Dude = ExampleDatabase.BookshelfSchema.Dude

fun Application.configureRouting() {
    routing {
        // TODO - web package
        // static {
        //     resources("web")
        // }
        staticResources("/static/", "static")

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
                            val dudes = Dude.browse().toList()
                            for (book in Book) {
                                val authorName = book.author().name
                                val currentBorrower = book.currentBorrower()
                                li {
                                    +book.title
                                    i { +" by " }
                                    +authorName
                                    br()
                                    if (currentBorrower != null) {
                                        +"borrowed by ${currentBorrower.name} on ${currentBorrower.borrowingDate}"
                                        br()
                                        form(classes="restitute-form") {
                                            attributes["data-book_id"] = "${book.bookId}"
                                            button(type=ButtonType.submit) {
                                                +"restitute"
                                            }
                                        }
                                    } else {
                                        +"available"
                                        br()
                                        form(classes="lend-form") {
                                            attributes["data-book_id"] = "${book.bookId}"
                                            +"lend to "
                                            select() {
                                                attributes["name"] = "dude_id"
                                                for (dude in dudes) {
                                                    option {
                                                        attributes["value"] = dude.dudeId.toString()
                                                        +dude.name
                                                    }
                                                }
                                            }
                                            button(type=ButtonType.submit, classes = "lend") {
                                                +"go"
                                            }
                                        }
                                    }
                                    br()
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

        trace { application.log.warn(it.buildText()) }
    }
}

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
        value: Any?
    ): OutgoingContent? = TextContent(value?.toString() ?: "", contentType.withCharsetIfNeeded(charset))
}
