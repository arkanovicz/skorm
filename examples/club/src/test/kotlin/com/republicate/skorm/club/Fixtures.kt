package com.republicate.skorm.club

import com.republicate.kson.toJsonObject
import com.republicate.skorm.core.scalarSetAttribute
import kotlinx.coroutines.flow.toList

/** a configuration of one in-memory H2 database, [name] apart from the other test classes' */
fun h2(name: String) = mapOf("core" to mapOf("jdbc" to mapOf("url" to "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"))).toJsonObject()

class Rollback : Exception("rollback requested")

/** the tables of the bookshelf and club schemas, as `schema.table`: a test's own attribute */
fun MutableClubDatabase.registerTables() {
    club.scalarSetAttribute<String>("tables", """
        SELECT LOWER(table_schema || '.' || table_name) FROM information_schema.tables
          WHERE LOWER(table_schema) IN ('bookshelf', 'club') ORDER BY 1;""".trimIndent())
}

suspend fun MutableClubDatabase.tables(): List<String> = club.harvest<String>("tables").toList()

val ALL_TABLES = listOf(
    "bookshelf.author", "bookshelf.book", "bookshelf.book_tag", "bookshelf.borrowing", "bookshelf.dude", "bookshelf.tag",
    "club.member", "club.review"
)
