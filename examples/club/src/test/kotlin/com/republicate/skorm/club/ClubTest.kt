package com.republicate.skorm.club

import com.republicate.skorm.MutableInstance
import com.republicate.skorm.bookshelf.*
import com.republicate.skorm.core.CoreProcessor
import com.republicate.skorm.jdbc.JdbcConnector
import com.republicate.skorm.transaction
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import kotlin.test.*

/** The app knows only the club's database: the bookshelf's comes up with it, on its processor. */
class ClubTest {

    companion object {
        lateinit var database: MutableClubDatabase
        var created = false
        var createdAgain = true

        @BeforeAll @JvmStatic
        fun bringUp(): Unit = runBlocking {
            database = MutableClubDatabase(CoreProcessor(JdbcConnector()))
            database.configure(h2("club"))
            database.initialize()
            created = database.createMissingSchemas()
            createdAgain = database.createMissingSchemas()
            database.registerTables()
        }
    }

    /** an author and their book, through the bookshelf's own typed API */
    private suspend fun book(title: String, author: String = "Ursula K. Le Guin"): MutableBook {
        val writer = MutableAuthor.new().apply { name = author; insert() }
        return MutableBook.new().apply { this.title = title; authorId = writer.authorId; genre = Genre.novel; insert() }
    }

    private suspend fun member(name: String, dude: MutableDude? = null) =
        MutableMember.new().apply { this.name = name; this.dude = dude?.dudeId; insert() }

    private suspend fun review(book: MutableBook, member: MutableMember, stars: Int = 5) =
        MutableReview.new().apply { this.book = book.bookId; memberId = member.memberId; this.stars = stars; insert() }

    @Test
    fun bringUpCreatesBothDatabases() = runBlocking {
        assertSame(MutableExampleDatabase.instance, database.example)
        assertSame(ClubDatabase.instance, database.readOnly)
        assertSame(ExampleDatabase.instance, database.readOnly.example)
        assertSame(ExampleDatabase.instance, database.example.readOnly)
        assertTrue(database.example.populated)
        assertTrue(created)
        assertFalse(createdAgain)
        assertEquals(ALL_TABLES, database.tables())
        println("bring-up: ${database.tables()}")
    }

    @Test
    fun navigationsCrossTheImport() = runBlocking {
        val dispossessed = book("The Dispossessed")
        val bob = MutableDude.new().apply { name = "Bob"; insert() }
        val alice = member("Alice")
        val bobby = member("Bobby", bob)
        val review = review(dispossessed, alice, stars = 4)
        review(book("The Left Hand of Darkness"), alice)

        // from the mutable half, the bookshelf's mutable row
        val navigated = review.book()
        assertIs<MutableBook>(navigated)
        assertEquals("The Dispossessed", navigated.title)
        // then the bookshelf's own navigation from it
        assertEquals("Ursula K. Le Guin", navigated.author().name)

        // from the read-only sibling, the bookshelf's read-only row
        val readOnlyReview = Review.fetch(review.reviewId)!!
        assertIsNot<MutableInstance>(readOnlyReview)
        val readOnlyBook = readOnlyReview.book()
        assertIs<Book>(readOnlyBook)
        assertIsNot<MutableInstance>(readOnlyBook)
        assertEquals("The Dispossessed", readOnlyBook.title)
        assertEquals("Ursula K. Le Guin", readOnlyBook.author().name)

        // the nullable link, both ways
        assertNull(Member.fetch(alice.memberId)!!.dude())
        assertEquals("Bob", Member.fetch(bobby.memberId)!!.dude()?.name)

        // the link between the club's own tables
        assertEquals("Alice", review.member().name)
        assertEquals(2, alice.reviews().count())

        // the join table with an imported table: the collection is on the club's side, of the bookshelf's rows
        MutableWish.new(alice.memberId, dispossessed.bookId).insert()
        assertIs<MutableBook>(alice.books().toList().single())
        val wished = Member.fetch(alice.memberId)!!.books().toList()
        assertTrue(wished.all { it is Book && it !is MutableInstance })
        assertEquals(listOf("The Dispossessed"), wished.map { it.title })
        assertEquals(0, bobby.books().count())

        // the ksql attribute returning the bookshelf's rows
        val reviewed = Member.fetch(alice.memberId)!!.reviewedBooks().toList()
        assertTrue(reviewed.all { it is Book && it !is MutableInstance })
        assertEquals(listOf("The Dispossessed", "The Left Hand of Darkness"), reviewed.map { it.title })
        println("navigations: ${navigated::class.simpleName} ${readOnlyBook::class.simpleName}, reviewed ${reviewed.map { it.title }}")
    }

    @Test
    fun oneTransactionAcrossBothDatabases() = runBlocking {
        val reader = member("Carol")
        val books = Book.browse().count()
        val reviews = Review.browse().count()

        assertFailsWith<Rollback> {
            database.transaction {
                review(book("Ghost"), reader)
                // the read-only siblings join the transaction: they see what it has not committed
                assertEquals(books + 1, Book.browse().count())
                assertEquals(reviews + 1, Review.browse().count())
                throw Rollback()
            }
        }
        assertEquals(books, Book.browse().count())
        assertEquals(reviews, Review.browse().count())
        assertTrue(Book.browse().map { it.title }.toList().none { it == "Ghost" })

        database.transaction {
            review(book("Solaris", "Stanisław Lem"), reader)
        }
        assertEquals(books + 1, Book.browse().count())
        assertEquals(reviews + 1, Review.browse().count())
        assertEquals("Solaris", reader.reviews().toList().single().book().title)
        println("transaction: rolled back to $books books and $reviews reviews, then committed one of each")
    }

    @Test
    fun unqualifiedNamesInsideTheOtherDatabasesTransaction() = runBlocking {
        val reader = member("Dave")

        // a club transaction runs the bookshelf's `SELECT count(*) FROM book`
        database.transaction {
            val books = database.example.bookshelf.booksCount()
            book("Counted in a club transaction")
            assertEquals(books + 1, database.example.bookshelf.booksCount())
            println("bookshelf.booksCount in a club transaction: $books, then ${books + 1}")
        }

        // a bookshelf transaction runs the club's `SELECT count(*) FROM review`
        database.example.transaction {
            val reviews = database.club.reviewsCount()
            review(book("Reviewed in a bookshelf transaction"), reader)
            assertEquals(reviews + 1, database.club.reviewsCount())
            println("club.reviewsCount in a bookshelf transaction: $reviews, then ${reviews + 1}")
        }
    }
}
