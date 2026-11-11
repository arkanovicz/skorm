package com.republicate.skorm.club

import com.republicate.skorm.SkormException
import com.republicate.skorm.bookshelf.MutableExampleDatabase
import com.republicate.skorm.core.CoreProcessor
import com.republicate.skorm.jdbc.JdbcConnector
import kotlin.test.*

/** Imported databases are parts of one physical database: another processor is refused. */
class ForeignProcessorTest {

    @Test
    fun importedDatabaseOnAnotherProcessor() {
        MutableExampleDatabase(CoreProcessor(JdbcConnector())).apply { configure(h2("foreign_bookshelf")); initialize() }
        val club = MutableClubDatabase(CoreProcessor(JdbcConnector())).apply { configure(h2("foreign_club")) }
        val failure = assertFailsWith<SkormException> { club.initialize() }
        assertEquals("database example is imported by database club: they must be created on the same processor", failure.message)
        println("foreign processor: ${failure.message}")
    }
}
