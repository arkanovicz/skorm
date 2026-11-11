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
        assertEquals("Flow<ShelfDatabase.MainSchema.Book>", books.returnType)
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

    private val campus = """
        database campus {
          schema main {
            table student {
              *id serial
              name varchar(20)
            }
            table course { *id serial }
            table lesson { *id serial }
            table follows {
              follower -- student
              followed -- student
            }
            student *-* course
            lesson *-- course
          }
        }
    """.trimIndent()

    @Test
    fun `a many-to-many between two tables keyed alike names its columns as the database does`() {
        val model = resolve(campus)
        val courses = model.join("Student", "courses")
        val students = model.join("Course", "students")
        assertEquals(listOf("id"), courses.params)
        assertTrue(courses.sql.endsWith("ON towards_table.id = join_table.course_id WHERE join_table.student_id = {id};"), courses.sql)
        assertTrue(students.sql.endsWith("ON towards_table.id = join_table.student_id WHERE join_table.course_id = {id};"), students.sql)
        // the join table is an entity too: its fields are what the runtime makes of those columns
        val join = model.schemas.single().entities.single { it.tableName == "student_course" }
        assertEquals(listOf("studentId", "courseId"), join.fields.map { it.name })
    }

    @Test
    fun `a link between two tables keyed alike is named after the table, its column as the database names it`() {
        val model = resolve(campus)
        val course = model.join("Lesson", "course")
        val lessons = model.join("Course", "lessons")
        assertEquals(listOf("course_id"), course.params)
        assertEquals("SELECT * FROM main.course WHERE course.id = {course_id};", course.sql)
        assertEquals("SELECT * FROM main.lesson WHERE lesson.course_id = {id};", lessons.sql)
        val lesson = model.schemas.single().entities.single { it.tableName == "lesson" }
        assertEquals(listOf("id", "courseId"), lesson.fields.map { it.name })
    }

    @Test
    fun `a table linked to itself many-to-many declares its join table, and gets both collections`() {
        val refused = assertThrows(com.republicate.kddl.SemanticException::class.java) {
            resolve("database d { schema s { table student { *id serial }\n student *-* student } }")
        }
        assertTrue(refused.message!!.contains("declare the join table"), refused.message)

        val model = resolve(campus)
        val followeds = model.join("Student", "followeds")
        val followers = model.join("Student", "followers")
        assertTrue(followeds.sql.endsWith("ON towards_table.id = join_table.followed WHERE join_table.follower = {id};"), followeds.sql)
        assertTrue(followers.sql.endsWith("ON towards_table.id = join_table.follower WHERE join_table.followed = {id};"), followers.sql)
    }

    @Test
    fun `an attribute on a table of several words registers on the entity as it is named`() {
        val model = resolve("""
            database d {
              schema s {
                table book_author { *id serial }
              }
            }
        """.trimIndent(), """
            database d {
              schema s {
                attr BookAuthor.rank: Int =
                  SELECT count(*) FROM book_author WHERE id <= {id};
                attr book_author.older: Int =
                  SELECT count(*) FROM book_author WHERE id < {id};
              }
            }
        """.trimIndent())
        val entity = model.schemas.single().entities.single()
        val rank = model.attributes.single { it.name == "rank" }
        // written as the table or as the class, the receiver is the same entity
        assertEquals(rank.receiverClass, model.attributes.single { it.name == "older" }.receiverClass)
        assertEquals("bookAuthor", entity.objectName)
        assertEquals(".entity(\"bookAuthor\").instanceAttributes", rank.registrationPath)
        assertEquals("DDatabase.SSchema.BookAuthor", rank.receiverClass)
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
    fun `a link column ending with the referenced key drops it, like _id`() {
        val model = resolve("""
            database d { schema s {
              table club { *code char(3) }
              table team { *id int }
              table member {
                name varchar(10)
                club_code -> club     // `_code` suffix, the club's key: `club`, not `clubCode`
                owner_code -> club?   // role plus key: `owner`
                donor -> club?        // role alone: unchanged
                team_id -> team       // `_id` suffix of an `id` key: `team`
              }
            } }
        """.trimIndent())
        val names = model.joins.filter { it.receiverClass.endsWith(".Member") }.map { it.name }.toSet()
        assertEquals(setOf("club", "owner", "donor", "team"), names)
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

    private val hierarchy = """
        database d { schema s {
          table person { name varchar(10) }
          table vip : person { since date }
          table star : vip { fans int }
          table address { city varchar(10)  owner -- person }
        } }
    """.trimIndent()

    @Test
    fun `a subtype declares its own columns, carries them all, and keeps its parent's key`() {
        val model = resolve(hierarchy)
        val entities = model.schemas.single().entities.associateBy { it.className }
        val person = entities.getValue("Person")
        val vip = entities.getValue("Vip")
        val star = entities.getValue("Star")
        assertNull(person.parentClass)
        assertEquals("DDatabase.SSchema.Person", vip.parentClass)
        assertEquals(listOf("since"), vip.ownFields.map { it.name })
        assertEquals(listOf("name", "personId", "kind", "since"), vip.fields.map { it.name })
        assertTrue(vip.hasPrimaryKey)
        assertTrue(vip.fields.single { it.name == "personId" }.primaryKey)
        assertFalse(star.fields.single { it.name == "kind" }.writable)
    }

    @Test
    fun `a table with descendants reads them joined, and knows the class each kind builds`() {
        val model = resolve(hierarchy)
        val entities = model.schemas.single().entities.associateBy { it.className }
        assertEquals("s.person LEFT JOIN s.base_vip USING (person_id) LEFT JOIN s.base_star USING (person_id)",
            entities.getValue("Person").source)
        assertEquals("s.vip LEFT JOIN s.base_star USING (person_id)", entities.getValue("Vip").source)
        assertNull(entities.getValue("Star").source)
        assertEquals(listOf("vip" to "DDatabase.SSchema.Vip", "star" to "DDatabase.SSchema.Star"), entities.getValue("Person").kinds)
        assertEquals(emptyList<Pair<String, String>>(), entities.getValue("Address").kinds)
        assertEquals(listOf("person", "vip", "star", null), listOf("Person", "Vip", "Star", "Address").map { entities.getValue(it).kindValue })
    }

    @Test
    fun `a navigation to a hierarchy member reads it from its source`() {
        val owner = resolve(hierarchy).join("Address", "owner")
        assertEquals("SELECT * FROM s.person LEFT JOIN s.base_vip USING (person_id) LEFT JOIN s.base_star USING (person_id) WHERE person.person_id = {owner};", owner.sql)
        val addresses = resolve(hierarchy).join("Person", "addresses")
        assertEquals("SELECT * FROM s.address WHERE address.owner = {person_id};", addresses.sql)
    }

    @Test
    fun `two subtypes declaring the same column are refused`() {
        val ex = assertThrows(SkormException::class.java) {
            resolve("""
                database d { schema s {
                  table person { name varchar(10) }
                  table vip : person { badge varchar(10) }
                  table staff : person { badge varchar(10) }
                } }
            """.trimIndent())
        }
        assertTrue(ex.message!!.contains("badge") && ex.message!!.contains("vip"), ex.message)
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

    @Test
    fun `a composite attribute whose class would be named like an entity is refused`() {
        val ex = assertThrows(SkormException::class.java) {
            resolve(shelf, """
                database shelf { schema main {
                  attr Book.dude: (name: String, given: Int)* = SELECT name, 1 AS given FROM dude;
                } }
            """.trimIndent())
        }
        assertTrue(ex.message!!.contains("Book.dude") && ex.message!!.contains("table dude"), ex.message)
    }

    /** The static list in [Collisions] must match what reflection sees here, where the full classpath exists. */
    @Test
    fun `the inherited-member list is what InstanceImpl and MutableInstanceImpl actually expose`() {
        // a function collides by name; a parameterless getX/isX is also a property, which a column would override
        val reflected = (com.republicate.skorm.InstanceImpl::class.java.methods + com.republicate.skorm.MutableInstanceImpl::class.java.methods)
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

    @Test
    fun `an argument may be typed by an enum of the schema, and by nothing else`() {
        val model = resolve("""
            database d { schema s {
              table book { title varchar(10)  genre enum('essay', 'novel') }
            } }
        """.trimIndent(), """
            database d { schema s {
              attr countIn(genre: Genre): Int = SELECT count(*) FROM book WHERE genre = {genre};
            } }
        """.trimIndent())
        assertEquals(listOf("genre" to "Genre"), model.attributes.single().arguments)

        val ex = assertThrows(SkormException::class.java) {
            resolve("database d { schema s { table book { title varchar(10) } } }", """
                database d { schema s { attr countIn(genre: Colour): Int = SELECT count(*) FROM book; } }
            """.trimIndent())
        }
        assertTrue(ex.message!!.contains("countIn") && ex.message!!.contains("Colour"), ex.message)
    }

    @Test
    fun `a parameter is spelled like a label, one letter or a capital included`() {
        val sample = resolve(shelf, """
            database shelf { schema main {
              attr Book.sample(n: Int, maxN: Int): Book* = SELECT * FROM book WHERE book_id > {book_id} LIMIT {n} OFFSET {maxN};
            } }
        """.trimIndent()).attributes.single()
        assertEquals(listOf("n" to "Int", "maxN" to "Int"), sample.arguments)
        assertEquals(setOf("book_id", "n", "maxN"), com.republicate.skorm.core.AttributeDefinition.parse(sample.sql).parameters())
    }

    @Test
    fun `a list argument is a Kotlin List, nullable or not, naming its element type`() {
        val among = resolve(shelf, """
            database shelf { schema main {
              attr among(ids: Int*, titles: String*?): Book* = SELECT * FROM book WHERE book_id = ANY({ids}) OR title = ANY({titles});
            } }
        """.trimIndent()).attributes.single()
        assertEquals(listOf("ids" to "List<Int>", "titles" to "List<String>?"), among.arguments)
        assertTrue("Int" in among.types && "String" in among.types, among.types.toString())
    }

    @Test
    fun `a syntax error fails the parse, naming source, line and column`() {
        val ex = assertThrows(SkormException::class.java) {
            parseRuntimeModel(CharStreams.fromString("""
                database d { schema s {
                  attr broken: Int SELECT 1;
                } }
            """.trimIndent()), "broken.ksql")
        }
        assertTrue(ex.message!!.startsWith("broken.ksql: line 2:"), ex.message)
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
        // a composite is a read-only row, in both halves
        assertEquals("ShelfDatabase.MainSchema.DudeImpl", borrower.composite!!.parentClass)
        assertEquals(listOf("borrowingDate"), borrower.composite.fields.map { it.name })
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

    private val twoSchemas = """
        database d {
          schema main { table person { name varchar(10) } }
          schema other { table badge { label varchar(10) } badge *-- main.person }
        }
    """.trimIndent()

    private fun attributesOf(ksql: String) = resolve(twoSchemas, "database d { schema other {\n$ksql\n} }").attributes

    @Test
    fun `an entity type qualified by its schema names that schema's class, single, nullable or multiple`() {
        val (holder, maybe, all) = attributesOf("""
            attr Badge.holder: main.Person = SELECT * FROM main.person WHERE person_id = {person_id};
            attr Badge.maybe: main.Person? = SELECT * FROM main.person WHERE person_id = {person_id};
            attr Badge.all: main.Person* = SELECT * FROM main.person;
        """.trimIndent())
        listOf(holder, maybe, all).forEach {
            assertEquals("DDatabase.OtherSchema.Badge", it.receiverClass)
            assertEquals("DDatabase.MainSchema.Person::new", it.factory)
            assertTrue(it.entityRows)
        }
        assertEquals("<DDatabase.MainSchema.Person>" to "retrieve", holder.generics to holder.verb)
        assertEquals("rowAttribute<DDatabase.MainSchema.Person>", holder.coreRegistration)
        assertEquals("instanceAttribute<DDatabase.MainSchema.Person>", holder.clientRegistration)
        assertEquals("<DDatabase.MainSchema.Person?>" to "retrieve", maybe.generics to maybe.verb)
        assertEquals("nullableRowAttribute<DDatabase.MainSchema.Person>", maybe.coreRegistration)
        assertEquals("nullableInstanceAttribute<DDatabase.MainSchema.Person>", maybe.clientRegistration)
        assertEquals("<DDatabase.MainSchema.Person>" to "query", all.generics to all.verb)
        assertEquals("rowSetAttribute<DDatabase.MainSchema.Person>", all.coreRegistration)
        assertEquals(all.coreRegistration, all.clientRegistration)
        // and the mutable half names the mutable class of that schema
        val mutableAll = resolve(twoSchemas, "database d { schema other { attr all: main.Person* = SELECT * FROM main.person; } }")
            .mutableTwin!!.attributes.single()
        assertEquals("MutableDDatabase.MutableMainSchema.MutablePerson::new", mutableAll.factory)
    }

    @Test
    fun `a composite may build on an entity of another schema, read-only like any composite`() {
        val holder = attributesOf("""
            attr Badge.holder: (main.Person, badges: Int)? = SELECT person.*, 1 AS badges FROM main.person WHERE person_id = {person_id};
        """.trimIndent()).single()
        assertEquals("DDatabase.MainSchema.PersonImpl", holder.composite!!.parentClass)
        assertEquals("nullableRowAttribute<Holder>", holder.coreRegistration)
    }

    @Test
    fun `a schema and a table are found by their SQL name, and qualifying by the block's own schema changes nothing`() {
        val spellings = attributesOf("""
            attr a: main.Person* = SELECT * FROM main.person;
            attr b: Main.person* = SELECT * FROM main.person;
            attr c: other.Badge* = SELECT * FROM other.badge;
            attr d: Badge* = SELECT * FROM other.badge;
        """.trimIndent())
        // what the type decides
        fun typed(a: com.republicate.skorm.resolve.QueryAttribute) =
            listOf(a.verb, a.entityRows, a.generics, a.cast, a.coreRegistration, a.clientRegistration, a.factory, a.types)
        val (a, b, c, d) = spellings.map { typed(it) }
        assertEquals(a, b)
        assertEquals(d, c)
        assertEquals("rowSetAttribute<DDatabase.OtherSchema.Badge>", spellings[3].coreRegistration)
    }

    @Test
    fun `an unknown schema or table in an entity type is refused, naming the attribute and what is missing`() {
        val noSchema = assertThrows(SkormException::class.java) { attributesOf("attr Badge.holder: elsewhere.Person = SELECT 1;") }
        assertTrue(noSchema.message!!.contains("Badge.holder") && noSchema.message!!.contains("no schema elsewhere"), noSchema.message)
        val noTable = assertThrows(SkormException::class.java) { attributesOf("attr everybody: (main.Nobody, n: Int)* = SELECT 1;") }
        assertTrue(noTable.message!!.contains("everybody") && noTable.message!!.contains("no table Nobody in schema main"), noTable.message)
        val unqualified = assertThrows(SkormException::class.java) { attributesOf("attr everybody: Person* = SELECT 1;") }
        assertTrue(unqualified.message!!.contains("no table Person in schema other"), unqualified.message)
    }

    @Test
    fun `a block of statements is performed as one transaction, its statements handed over without the braces`() {
        val forget = resolve(shelf, """
            database shelf { schema main {
              mut Dude.forget = {
                DELETE FROM borrowing WHERE dude_id = {dude_id};
                DELETE FROM dude WHERE dude_id = {dude_id};
              }
            } }
        """.trimIndent()).mutableTwin!!.attributes.single()
        assertEquals("perform", forget.verb)
        assertEquals("mutationAttribute", forget.coreRegistration)
        assertEquals(
            "DELETE FROM borrowing WHERE dude_id = {dude_id};\nDELETE FROM dude WHERE dude_id = {dude_id};",
            forget.sql
        )
    }

    @Test
    fun `a mutation performs and registers as such, a multiple composite queries a row set`() {
        val model = resolve(shelf, shelfSql)
        // a mutation belongs to the mutable half alone
        assertTrue(model.attributes.none { it.name == "lend" })
        val lend = model.mutableTwin!!.attributes.single { it.name == "lend" }
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
