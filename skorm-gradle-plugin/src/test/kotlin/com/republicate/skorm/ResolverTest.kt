package com.republicate.skorm

import com.republicate.kddl.parse
import com.republicate.skorm.resolve.JoinAttribute
import com.republicate.skorm.resolve.Resolver
import com.republicate.skorm.resolve.ResolvedModel
import org.antlr.v4.kotlinruntime.CharStreams
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The resolved model is the one place a navigation or an attribute is named, typed and directed:
 * these tests state what that place must say. The golden tests only say the output did not move.
 */
class ResolverTest {

    private fun resolve(kddl: String, ksql: String? = null): ResolvedModel {
        val database = parse(CharStreams.fromString(kddl))
        val attributes = ksql?.let { parseRuntimeModel(CharStreams.fromString(it)) }
        return Resolver().resolve(database, attributes)
    }

    private fun ResolvedModel.join(receiver: String, name: String): JoinAttribute =
        joins.single { it.receiverClass.endsWith(".$receiver") && it.name == name }

    private val shelf = """
        database shelf {
          schema main {
            table dude { !name varchar(100) }
            table author { name varchar(100) }
            table tag { label varchar(30) }
            table book {
              title varchar(100)
              donor -- dude?
            }
            table borrowing { borrowing_date date }
            book *-- author
            borrowing -> book
            book *-* tag
          }
        }
    """.trimIndent()

    @Test
    fun `a nullable forward link is nullable in its accessor and its registration alike`() {
        val donor = resolve(shelf).join("Book", "donor")
        assertEquals("ShelfDatabase.MainSchema.Dude?", donor.returnType)
        assertEquals("retrieve", donor.verb)
        assertEquals("nullableRowAttribute", donor.registration)
        // the parameter is the foreign-key column, which the receiving row holds
        assertEquals(listOf("donor"), donor.params)
        assertEquals("SELECT * FROM main.dude WHERE dude.dude_id = {donor};", donor.sql)
    }

    @Test
    fun `a reverse link is a collection on the referenced side, keyed by that side's primary key`() {
        val model = resolve(shelf)
        assertEquals("book", model.join("Book", "donor").ownerEntity)
        val books = model.join("Dude", "books")
        assertEquals("dude", books.ownerEntity)
        assertEquals("Sequence<ShelfDatabase.MainSchema.Book>", books.returnType)
        assertEquals("query", books.verb)
        assertEquals("rowSetAttribute", books.registration)
        assertEquals(listOf("dude_id"), books.params)
        assertEquals("SELECT * FROM main.book WHERE book.donor = {dude_id};", books.sql)
    }

    @Test
    fun `a chevron withholds the collection`() {
        val model = resolve(shelf)
        assertNotNull(model.joins.singleOrNull { it.receiverClass.endsWith(".Borrowing") && it.name == "book" })
        assertTrue(model.joins.none { it.receiverClass.endsWith(".Book") && it.name == "borrowings" })
        // and `*--` keeps it
        assertNotNull(model.joins.singleOrNull { it.receiverClass.endsWith(".Author") && it.name == "books" })
    }

    @Test
    fun `a many-to-many names each collection after the far side and keys it by the near side`() {
        val model = resolve(shelf)
        val tags = model.join("Book", "tags")
        val books = model.join("Tag", "books")
        assertEquals("ShelfDatabase.MainSchema.Tag", tags.targetClass)
        assertEquals(listOf("book_id"), tags.params)
        assertTrue(tags.sql.contains("JOIN main.tag AS towards_table") && tags.sql.endsWith("{book_id};"))
        assertEquals("ShelfDatabase.MainSchema.Book", books.targetClass)
        assertEquals(listOf("tag_id"), books.params)
        assertTrue(books.sql.contains("JOIN main.book AS towards_table") && books.sql.endsWith("{tag_id};"))
    }

    @Test
    fun `two links to the same table are told apart by their column`() {
        val model = resolve("""
            database d { schema s {
              table person { name varchar(10) }
              table friendship {
                *a_id -- person
                *b_id -- person
                since date            // without it, two links and nothing else make a junction table
              }
            } }
        """.trimIndent())
        assertEquals(setOf("a", "b"), model.joins.filter { it.receiverClass.endsWith(".Friendship") }.map { it.name }.toSet())
        assertEquals(setOf("aFriendships", "bFriendships"), model.joins.filter { it.receiverClass.endsWith(".Person") }.map { it.name }.toSet())
    }

    @Test
    fun `a cross-schema link names each end in its own schema and registers on the owning entity`() {
        val model = resolve("""
            database d {
              schema main { table person { name varchar(10) } }
              schema other { table badge { label varchar(10) } badge *-- main.person }
            }
        """.trimIndent())
        val person = model.join("Badge", "person")
        assertEquals("DDatabase.MainSchema.Person", person.targetClass)
        assertEquals("other" to "badge", person.ownerSchema to person.ownerEntity)
        val badges = model.join("Person", "badges")
        assertEquals("DDatabase.MainSchema.Person", badges.receiverClass)
        assertEquals("DDatabase.OtherSchema.Badge", badges.targetClass)
        assertEquals("main" to "person", badges.ownerSchema to badges.ownerEntity)
    }

    @Test
    fun `a link whose column is the referenced key is named after the table, otherwise after the column`() {
        val model = resolve("""
            database d { schema s {
              table country { *code char(2) }
              table author { name varchar(10) }
              table book {
                title varchar(10)
                donor -> author?      // named column: keeps its name
              }
              book *-- author         // implicit author_id: the table
              book --> country        // implicit code, the country's key: the table, not `code`
                                      // (one-way, or Country.books would collide with the n-n below)
              book *-* country        // junction over a code-keyed table: `countries`, not `codes`
            } }
        """.trimIndent())
        val names = model.joins.filter { it.receiverClass.endsWith(".Book") }.map { it.name }.toSet()
        assertEquals(setOf("donor", "author", "country", "countries"), names)
    }

    @Test
    fun `a hierarchy's kind is an enum the database maintains, so it is read-only`() {
        val person = resolve("""
            database d { schema s {
              table person { name varchar(10) }
              table vip : person
            } }
        """.trimIndent()).schemas.single().entities.single { it.className == "Person" }
        val kind = person.fields.single { it.name == "kind" }
        assertEquals("PersonKind", kind.kotlinType)
        assertFalse(kind.writable)
        assertTrue(person.fields.single { it.name == "name" }.writable)
    }

    @Test
    fun `fields carry their Kotlin type, getter and enum class`() {
        val entity = resolve("""
            database d { schema s {
              table book {
                title varchar(100)
                genre enum('essay', 'novel')
                donor int?
              }
              table loan { since date }
              loan -> book
            } }
        """.trimIndent()).schemas.single().entities.single { it.className == "Book" }
        val byName = entity.fields.associateBy { it.name }
        assertEquals("String", byName.getValue("title").kotlinType)
        assertEquals("getString", byName.getValue("title").getter)
        assertEquals("Genre", byName.getValue("genre").enumClass)
        assertTrue(byName.getValue("donor").nullable)
        assertTrue(byName.getValue("bookId").primaryKey && byName.getValue("bookId").generated)
    }

    @Test
    fun `a keyword is spelled with backticks where it is declared`() {
        val model = resolve("""
            database d { schema s {
              table person { name varchar(10) }
              table pair {
                object -> person     // a link column named after a Kotlin keyword
                fun varchar(10)      // a plain column too
              }
            } }
        """.trimIndent())
        val link = model.joins.single()
        assertEquals("object", link.name)
        assertEquals("`object`", link.identifier)
        val column = model.schemas.single().entities.single { it.className == "Pair" }.fields.single { it.name == "fun" }
        assertEquals("`fun`", column.identifier)
    }

    @Test
    fun `a column overriding an inherited row member is refused, naming the table and column`() {
        val ex = assertThrows(SkormException::class.java) {
            resolve("database d { schema s { table t { size int } } }")
        }
        assertTrue(ex.message!!.contains("table t") && ex.message!!.contains("'size'"), ex.message)
        assertTrue("entity" in com.republicate.skorm.resolve.Collisions.inherited)
        assertTrue("isPersisted" in com.republicate.skorm.resolve.Collisions.inherited)
    }

    @Test
    fun `an attribute named like a navigation on the same receiver is refused`() {
        val ex = assertThrows(SkormException::class.java) {
            resolve(shelf, """
                database shelf { schema main {
                  attr Author.books: Int = SELECT count(*) FROM book WHERE author_id = {author_id};
                } }
            """.trimIndent())
        }
        assertTrue(ex.message!!.contains("'books'") && ex.message!!.contains("reverse foreign key"), ex.message)
    }

    /** The static list in [Collisions] must match what reflection sees here, where the full classpath exists. */
    @Test
    fun `the inherited-member list is what Instance actually exposes`() {
        // a function collides by name; a parameterless getX/isX is also a property, which a column would override
        val reflected = com.republicate.skorm.Instance::class.java.methods
            .filter { '$' !in it.name }
            .flatMap { m ->
                val n = m.name
                val property = when {
                    m.parameterCount != 0 -> null
                    n.length > 3 && n.startsWith("get") && n[3].isUpperCase() -> n[3].lowercase() + n.substring(4)
                    n.length > 2 && n.startsWith("is") && n[2].isUpperCase() -> n
                    else -> null
                }
                listOfNotNull(n, property)
            }.toSet()
        val listed = com.republicate.skorm.resolve.Collisions.inherited
        println("INHERITED " + reflected.sorted().joinToString(" "))
        assertEquals(reflected, listed)
    }

    private val shelfSql = """
        database shelf {
          schema main {
            attr booksCount: Int = SELECT count(*) FROM book;
            attr Book.currentBorrower: (Dude, borrowing_date: LocalDate)? =
              SELECT dude.*, borrowing_date FROM borrowing JOIN dude USING (dude_id) WHERE book_id = {book_id};
            mut Book.lend(dude_id: Long) = INSERT INTO borrowing (dude_id, book_id) VALUES ({dude_id}, {book_id});
            attr Author.catalog: (title: String, borrowings: Int)* = SELECT title, 0 borrowings FROM book;
          }
        }
    """.trimIndent()

    @Test
    fun `a schema-level scalar attribute evaluates, an entity composite retrieves through its factory`() {
        val model = resolve(shelf, shelfSql)
        val count = model.attributes.single { it.name == "booksCount" }
        assertEquals("ShelfDatabase.MainSchema", count.receiverClass)
        assertEquals("", count.registrationPath)
        assertEquals("eval", count.verb)
        assertEquals("scalarAttribute<Int>", count.coreRegistration)
        assertNull(count.factory)

        val borrower = model.attributes.single { it.name == "currentBorrower" }
        assertEquals("ShelfDatabase.MainSchema.Book", borrower.receiverClass)
        assertEquals(".entity(\"book\").instanceAttributes", borrower.registrationPath)
        assertEquals("retrieve", borrower.verb)
        assertEquals(" as CurrentBorrower?", borrower.cast)
        assertEquals("nullableRowAttribute<CurrentBorrower>", borrower.coreRegistration)
        assertEquals(borrower.coreRegistration, borrower.clientRegistration)
        assertEquals("::CurrentBorrower", borrower.factory)
        assertEquals("ShelfDatabase.MainSchema.Dude", borrower.composite!!.parentClass)
        assertEquals(listOf("borrowingDate"), borrower.composite!!.fields.map { it.name })
    }

    @Test
    fun `a multiple entity attribute is a row set on the client as on the core`() {
        val everybody = resolve(shelf, """
            database shelf { schema main { attr everybody: Dude* = SELECT * FROM dude; } }
        """.trimIndent()).attributes.single()
        assertEquals("query", everybody.verb)
        assertEquals("rowSetAttribute<ShelfDatabase.MainSchema.Dude>", everybody.coreRegistration)
        assertEquals(everybody.coreRegistration, everybody.clientRegistration)
    }

    @Test
    fun `a mutation performs and registers as such, a multiple composite queries a row set`() {
        val model = resolve(shelf, shelfSql)
        val lend = model.attributes.single { it.name == "lend" }
        assertEquals("perform", lend.verb)
        assertEquals("", lend.generics)
        assertEquals("mutationAttribute", lend.coreRegistration)
        assertEquals(listOf("dude_id" to "Long"), lend.arguments)

        val catalog = model.attributes.single { it.name == "catalog" }
        assertEquals("query", catalog.verb)
        assertEquals("<Catalog>", catalog.generics)
        assertEquals("", catalog.cast)
        assertEquals("rowSetAttribute<Catalog>", catalog.coreRegistration)
        assertEquals("Json.MutableObject", catalog.composite!!.parentClass)
    }
}
