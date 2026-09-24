# skorm

*Simple Kotlin Object Relational Mapping*

The nicest Kotlin multiplatform ORM around. Fully multiplatform. Coroutines-enabled.

## Concepts

*Five* main concepts:

+ **Database** - Root container for your data model
+ **Schema** - Logical grouping of entities (see [Configuration](#configuration))
+ **Entity** - Corresponds to a table or view (defined in [kddl syntax](#kddl-syntax))
+ **Instance** - A single row in a table, read-only; a `MutableInstance` adds the writes
+ **Attribute** - Custom queries and mutations (see [ksql syntax](#ksql-syntax)), with five variants:

     + ScalarAttribute, returning Any?
     + RowAttribute, returning Instance?
     + RowSetAttribute, returning Sequence<Instance>
     + MutationAttribute, returning Long (either the number of modified rows, or the generated serial value)
     + TransactionAttribute, returning List<Long> (number of modified rows for each comprised mutation statement)

*Four* main methods in the lifecycle of database objects instances (along with transaction handling):

+ `MutableInstance.insert()`
+ `Entity.fetch(primaryKey)`
+ `MutableInstance.update()`
+ `MutableInstance.delete()`

The runtime has two halves: `Database`, `Schema`, `Entity`, `Instance` read; the objects of a mutable database also implement `MutableDatabase`, `MutableSchema`, `MutableEntity`, `MutableInstance`, which is where `perform` and the writes live. `Instance` is an interface over a read-only map (`InstanceImpl`); a `MutableInstance` is backed by a mutable one (`MutableInstanceImpl`) — a read-only row has no `put` at all.

*Three* main customization points (see [Configuration](#configuration)):

+ identifiers mapping (snake to camel, prefix/suffix removal, lowercase ...)
+ fields filtering (hide secret field, mark field as read-only, ...)
+ values filtering (transform timestamps, etc.)

*Two* model definition formats: kddl for <abbr title="Data Definition Language">DDL</abbr> (schema structure), ksql for <abbr title="Data Manipulation Language">DML</abbr> (custom queries and mutations). And *two* concrete database connectors, one for <abbr title="Java DataBase Connectivity">JDBC</abbr> and one for a service <abbr title="Application Programmable Interface">API</abbr>. More to come, hopefully.

*One* goal: maximum simplicity without conceeding anyting to extensibility.

*Zero* annotation. Zero <abbr title="Set Query Language">SQL</abbr> code fragmentation.

## Quick Start

Let's create a simple todo list application.

### 1. Define your schema (`todo.kddl`)

```
database todo_app {
  schema todos {
    table task {
      title string(200)
      completed boolean = false
    }
  }
}
```

### 2. Add custom queries and mutations (`todo.ksql`, optional)

```
database todo_app {
  schema todos {
    attr pendingCount: Int =
      SELECT count(*) FROM task WHERE completed = false;

    mut Task.toggle =
      UPDATE task SET completed = NOT completed WHERE task_id = {task_id};
  }
}
```

### 3. Configure the Gradle plugin (`build.gradle.kts`)

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("com.republicate.skorm") version "0.20"
}

skorm {
    model.set(file("src/commonMain/model/todo.kddl"))
    attributes.set(file("src/commonMain/model/todo.ksql"))    // optional
    destPackage.set("com.example.todo")
    dialect.set("postgresql")                                 // or "hypersql"
    // readOnly.set(true)                                     // only the read-only half, for a build that never writes
}

dependencies {
    // For JVM backend
    implementation("com.republicate.skorm:skorm-core")
    implementation("com.republicate.skorm:skorm-jdbc")

    // Or for JS/WASM client
    implementation("com.republicate.skorm:skorm-api-client")
}
```

Nothing else to declare: the single `generateSkormCode` task registers its output on your Kotlin
source sets, so Gradle sequences generation before compilation on its own — no `srcDir`, no
`dependsOn`. What it emits follows the platforms you build for, and `core` / `client` force either
one on or off, for instance to emit client code in a project that has no JS target of its own.

Options:

- `model` — the kddl model; or `datasource`, a JDBC URL to reverse-engineer it from. Exactly one.
- `attributes` — the ksql attributes declared over it. Without them, only the model is generated.
- `destPackage` — package of the generated code.
- `dialect` — `postgresql` or `hypersql`, required to emit the creation script.
- `core` / `client` — unset, they follow the project's targets; set, they force.
- `outputDirectory` — defaults to `build/generated-src`.

Generated code is laid out by *role*, not by source set, because one directory often feeds several
of them — `client` serves `jsMain`, `wasmJsMain` and `linuxX64Main` alike:

```
build/generated-src/
├── common/kotlin       entity classes, field interfaces, join and attribute accessors
├── core/kotlin         server-side attribute registrations
├── core/resources      database creation script
└── client/kotlin       REST client attribute registrations
```

`common` is registered on `commonMain`, `core` on the JVM target's source set, `client` on every
JS, wasm and native one. In a plain `kotlin("jvm")` project there is a single source set, and
`common` and `core` are both registered on `main`.

The generated types are nested (`ExampleDatabase.BookshelfSchema.Book`); alias in your own code
the ones you use, e.g. `typealias Book = ExampleDatabase.BookshelfSchema.Book`.

#### Two halves: read-only and mutable

The generator emits a read-only database and a mutable one extending it, so that code which must
not write — a render, a user-edited template — is handed a database that *cannot*, not one that
promises not to:

```
ExampleDatabase                          MutableExampleDatabase : ExampleDatabase, MutableDatabase
  BookshelfSchema                          MutableBookshelfSchema : BookshelfSchema, MutableSchema
    interface Book : Instance                interface MutableBook : Book, MutableInstance
      companion object : Entity               companion object : Entity, MutableEntity
      val title; fun author(); fun tags()      override var title; override fun tags(): Sequence<MutableTag>
      fun currentBorrower()                    fun lend(dude: Int)            // ksql mutations
    open class BookImpl : InstanceImpl, Book   open class MutableBookImpl : MutableInstanceImpl, MutableBook
```

A row type is an interface, so a table hierarchy (`MutableVip : Vip, MutablePerson`) and mutability
inherit side by side; the `Impl` class behind it is the storage, and carries the blocking twins.
The companion object is the entity: `Book.browse()`, `Book.fetch(id)` read through the read-only
database, `MutableBook.browse()`, `MutableBook.new()` through the mutable one. A row has no
constructor: `MutableBook.new()` builds one to insert. Read attributes and navigations are
declared once, on the read-only interface, and inherited; the mutable interface redeclares those
returning rows, covariantly, and adds the mutations.

Constructing a `MutableExampleDatabase` also constructs its read-only sibling over the same
processor, reachable as `mutableDb.readOnly` (and `ExampleDatabase.instance`): both share the
attribute registry and any ambient transaction, and the sibling reads on the processor's read
connector when one is configured. An app that never writes constructs `ExampleDatabase` alone —
and can set `readOnly` on the plugin, so that the mutable half is not even generated.

### 4. Use the generated code

```kotlin
// Initialize database (JVM): the mutable one, since we insert below
val database = MutableTodoAppDatabase(CoreProcessor(JdbcConnector()))
database.configure(mapOf(
    "jdbc" to mapOf(
        "url" to "jdbc:h2:mem:todo",
        "user" to "sa"
    )
))
database.initialize()

// Create a task
val task = MutableTask.new().apply {
    title = "Learn skorm"
    completed = false
    insert()
}

// Fetch and update
val fetched = MutableTask.fetch(task.taskId)
fetched?.let {
    it.completed = true
    it.update()
}

// Browse all tasks, read-only rows through the read-only sibling
Task.browse().forEach { println(it.title) }
```

That's it! The skorm Gradle plugin generates all the necessary Kotlin types from your `.kddl` file.

### Dynamic Usage (Without Code Generation)

You can also use skorm without the code generator, accessing entities dynamically:

```kotlin
// Navigate the model: a mutable database's entities hand out mutable rows
val schema = database.schema("todos")
val taskEntity = schema.entity("task")

// CRUD operations
val task = taskEntity.new() as MutableInstance
task["title"] = "Learn skorm"
task["completed"] = false
task.insert()

val fetched = taskEntity.fetch(task["taskId"]) as MutableInstance?
fetched?.let {
    it["completed"] = true
    it.update()
}

taskEntity.browse().forEach { println(it["title"]) }
```

This is useful for generic tools, migrations, or when the schema is only known at runtime.

### Transactions

Transactions are ambient: wrap any suspend code in `database.transaction { ... }` and every skorm operation against that database inside the block — entity ops, attributes, raw `eval`/`perform` — joins the same transaction, without passing any handle around.

```kotlin
database.transaction {        // or transaction("schema") { ... } for multi-schema databases
    task.update()
    MutableTask.new().apply { title = "follow-up"; insert() }
}                             // commit on exit; rollback (and rethrow) on any throw
```

Nested blocks on the same database join the enclosing transaction (single commit). The transaction holds one connection: don't fan out parallel coroutines inside the block, and iterate lazy `Sequence` results before the block exits. Not available in REST mode.

## Reference

### Configuration

```
Database *—— Schema *—— Entity *—— Instance
```

*Five* main verbs to interact with attributes:

+ `eval(name, params...)` - returns a scalar value
+ `retrieve(name, params...)` - returns a single row (plus `Entity.fetch(params...)` to get an instance by ID)
+ `query(name, params...)` - returns a rowset
+ `perform(name, params...)` - for mutations, on the mutable objects only; a `mut` declared as a block of statements (`= { …; …; }`) runs them in one transaction

#### Identifiers Mapping

Skorm automatically maps between database identifiers and Kotlin property names:

```kotlin
database.configure(mapOf(
    "core" to mapOf(
        "mapping" to mapOf(
            "read" to listOf("snakeToCamel"),   // DB columns: user_name → Kotlin: userName
            "write" to listOf("camelToSnake")   // Kotlin: userName → DB columns: user_name
        )
    )
))
```

Built-in mappers:
- `snakeToCamel` / `camelToSnake`
- `lowercase` / `uppercase`
- Custom mappers can be registered

#### Values Filtering

Transform values during read/write operations:

```kotlin
database.configure(mapOf(
    "core" to mapOf(
        "filter" to mapOf(
            "read" to mapOf(
                "timestamp" to "epochToLocalDateTime"
            ),
            "write" to mapOf(
                "timestamp" to "localDateTimeToEpoch"
            )
        )
    )
))
```

#### Connector Configuration

**JDBC Connector:**
```kotlin
database.configure(mapOf(
    "jdbc" to mapOf(
        "url" to "jdbc:postgresql://localhost:5432/mydb",
        "user" to "dbuser",
        "password" to "secret"
    )
))
```

**Read connector.** `CoreProcessor(connector, readConnector)` runs the reads of a read-only database on the second connector — a pool on a SELECT-only role is what makes the database actually read-only. It takes its settings from `core.read.<tag>` (`core.read.jdbc.url`, …), or the write connector's when absent. Inside a `transaction { }` of the mutable database, the read-only one reads on the transaction's connection.

**API Client (for JS/WASM):**
```kotlin
val database = TodoAppDatabase(ApiClient("https://api.example.com"))
database.initialize()
// Same code works on browser/Node.js!
```

### kddl Syntax

The kddl (Kotlin Data Definition Language) format defines your database structure. It generates both SQL DDL scripts and Kotlin classes.

#### Basic Structure

```
database <name> {
  schema <name> {
    table <name> {
      <field_name> <type> [modifiers]
    }
  }
}
```

#### Field Types

- **Strings**: `string`, `string(length)`, `text`
- **Numbers**: `int`, `long`, `float`, `double`, `decimal(p,s)`
- **Booleans**: `boolean`
- **Dates**: `date`, `time`, `datetime`, `timestamp`
- **Special**: `serial` (auto-increment), `uuid`, `json`
- **Enums**: `enum('value1', 'value2', ...)`

#### Field Modifiers

- `?` - nullable field
- `!` - unique constraint
- `= <value>` - default value

Example:
```
table user {
  !email string(255)              // unique, non-null
  name string(100)?               // nullable
  age int = 18                    // default value
  status enum('active', 'inactive') = 'active'
  created_at timestamp = now()
}
```

#### Primary Keys

Primary keys are auto-generated as `<table_name>_id` with type `serial`:

```
table book {
  title string
}
// Generates: book_id serial PRIMARY KEY
```

#### Relationships

A link's `*` marks the many side, which holds the foreign key; a chevron (`>`) pointing at the
*one* side restricts traversal to the reference. With no chevron, both navigations are generated.

```
book *-- author      // book holds author_id — Book.author() and Author.books()
borrowing -> book    // borrowing holds book_id — Borrowing.book() only
book *-* tag         // join table book_tag — Book.tags() and Tag.books()

table book {
  donor -- dude?     // field link, both ways — Book.donor() and Dude.donorBooks()
  editor -> dude?    // field link, reference only — Book.editor()
}
```

When two links reach the same table, as `donor` and `editor` do here, the collection is named
after the field (`donorBooks`) rather than the table (`books`).

A many-to-many is always navigable both ways. `a -- b` between two tables is rejected, nothing
saying which side holds the key (kddl ≥ 0.27).

The kddl compiler generates:
1. SQL DDL scripts for database creation
2. Kotlin row interfaces with typed properties, read-only and mutable
3. Navigation methods for the relationships above, as members of the generated interfaces — each with a
   blocking twin (`book.tagsBlocking()`, reachable by reflection as `tags()`) for callers that cannot suspend

For complete kddl documentation, see the [kddl project](https://github.com/arkanovicz/kddl).

### ksql Syntax

Beyond the basic CRUD operations, skorm allows you to define custom queries and mutations using the `ksql` format. These definitions generate type-safe Kotlin objects and member functions on their receivers.

#### Declaration Syntax

```
attr [Entity.]name: ReturnType = SQL
mut [Entity.]name[(params)] = SQL
```

- `attr` - defines a query attribute (SELECT)
- `mut` - defines a mutation attribute (INSERT/UPDATE/DELETE)
- Schema-level: `attr name` - function on schema
- Entity-level: `attr Entity.name` - function on entity instance

#### Return Types

| Syntax | Description | Example |
|--------|-------------|---------|
| `Type` | Non-nullable scalar | `Int`, `String`, `LocalDate` |
| `Type?` | Nullable scalar | `Int?`, `String?` |
| `(Entity, field: Type, ...)` | Composite object extending entity | `(Dude, borrowing_date: LocalDateTime)` |
| `(Entity, field: Type, ...)?` | Nullable composite | `(Dude, borrowing_date: LocalDateTime)?` |
| `(field: Type, ...)` | Anonymous object | `(count: Int, total: Double)` |
| `(field: Type, ...)?` | Nullable anonymous | `(count: Int, total: Double)?` |
| `(...)*` | Sequence of objects | `(name: String, count: Int)*` |

Supported scalar types: `Int`, `Long`, `String`, `Boolean`, `Double`, `Float`, `LocalDate`, `LocalDateTime`, `LocalTime`

#### Parameters

SQL parameters are enclosed in curly braces:

```kotlin
attr getUserByEmail: User? =
  SELECT * FROM users WHERE email = {email};
```

For entity-level attributes, all entity fields are automatically available:

```kotlin
attr Book.currentBorrower: Dude? =
  SELECT dude.* FROM borrowing
    JOIN dude USING (dude_id)
    WHERE book_id = {book_id}  // book_id from Book instance
    AND returned_date IS NULL;
```

Mutation parameters are declared in the signature:

```kotlin
mut Book.lend(dude_id: Long) =
  INSERT INTO borrowing (book_id, dude_id, borrowed_date)
    VALUES ({book_id}, {dude_id}, now());
```

#### Examples

**Schema-level scalar:**
```kotlin
attr booksCount: Int =
  SELECT count(*) FROM book;

// Generates: suspend fun BookshelfSchema.booksCount(): Int
```

**Entity-level composite object:**
```kotlin
attr Book.currentBorrower: (Dude, borrowing_date: LocalDateTime)? =
  SELECT dude.*, borrowing_date FROM bookshelf.borrowing
    JOIN dude USING (dude_id)
    WHERE book_id = {book_id}
    AND restitution_date IS NULL;

// Generates:
// class CurrentBorrower: Dude { val borrowingDate: LocalDateTime }
// suspend fun Book.currentBorrower(): CurrentBorrower?
```

**Mutation with parameters:**
```kotlin
mut Book.lend(dude_id: Long) =
  INSERT INTO borrowing (dude_id, book_id, borrowing_date)
    VALUES ({dude_id}, {book_id}, now());

// Generates: suspend fun Book.lend(dude_id: Long): Long
```

**Anonymous object:**
```kotlin
attr Book.stats: (title_length: Int, borrowed: Int) =
  SELECT
    CHARACTER_LENGTH(title) title_length,
    (SELECT COUNT(*) FROM borrowing WHERE book_id = {book_id}) borrowed
  FROM book
  WHERE book_id = {book_id};

// Generates:
// class Stats { val titleLength: Int; val borrowed: Int }
// suspend fun Book.stats(): Stats
```

**Sequence (rowset):**
```kotlin
attr topBorrowers: (dude_id: Long, borrow_count: Int)* =
  SELECT dude_id, COUNT(*) borrow_count
  FROM borrowing
  GROUP BY dude_id
  ORDER BY borrow_count DESC
  LIMIT 10;

// Generates:
// class TopBorrowers { val dudeId: Long; val borrowCount: Int }
// suspend fun BookshelfSchema.topBorrowers(): Sequence<TopBorrowers>
```

All generated functions are coroutine-based (`suspend`) and type-safe, providing compile-time checking of parameters and return types.

## Complete Example

Let's build a complete bookshelf application that tracks books and borrowings, demonstrating both JVM backend and JS frontend using the same business logic.

### Database Schema (`bookshelf.kddl`)

```
database example {
  schema bookshelf {

    table dude { name string }

    table author { name string }

    table book {
      title string
      genre enum('essay', 'literature', 'art')
      language string(2)
    }

    table borrowing {
      borrowing_date date = now()
      restitution_date date?
    }

    author *-* book
    book --> author
    borrowing -> book, dude
  }
}
```

This generates:
- SQL creation script
- Entity classes: `Dude`, `Author`, `Book`, `Borrowing`
- Relationship methods: `book.author()`, `author.books()`, `book.borrowings()`, etc.

### Custom Queries (`bookshelf.ksql`)

```kotlin
database example {
  schema bookshelf {

    // Schema-level scalar attribute
    attr booksCount: Int =
      SELECT count(*) FROM book;

    // Entity-level attribute returning a composite object
    attr Book.currentBorrower: (Dude, borrowing_date: LocalDateTime)? =
      SELECT dude.*, borrowing_date FROM bookshelf.borrowing
        JOIN dude USING (dude_id)
        WHERE book_id = {book_id}
        AND restitution_date IS NULL;

    // Mutation with parameters
    mut Book.lend(dude_id: Long) =
      INSERT INTO borrowing (dude_id, book_id, borrowing_date)
        VALUES ({dude_id}, {book_id}, now());

    // Mutation without parameters
    mut Book.restitute =
      UPDATE borrowing SET restitution_date = NOW()
        WHERE book_id = {book_id} AND restitution_date IS NULL;

    // Attribute returning an anonymous object
    attr Book.stats: (title_length: Int, borrowed: Int) =
      SELECT
        CHARACTER_LENGTH(title) title_length,
        (SELECT COUNT(*) FROM borrowing WHERE book_id = {book_id}) borrowed
      FROM book
      WHERE book_id = {book_id};

    // Attribute returning a sequence
    attr topBorrowers: (dude_id: Long, borrowed: Int)* =
      SELECT dude_id,
        COUNT(restitution_date) borrowed
      FROM borrowing
      GROUP BY dude_id
      ORDER BY borrowed DESC;
  }
}
```

### JVM Backend (Server.kt)

```kotlin
import com.republicate.skorm.core.CoreProcessor
import com.republicate.skorm.jdbc.JdbcConnector

val database = MutableExampleDatabase(CoreProcessor(JdbcConnector()))

fun Application.configureDatabase() {
    // Configure from application.conf
    database.configure(environment.config.config("skorm").toMap())
    database.initialize()
    database.initJoins()
    database.initRuntimeModel()

    // Create test data
    runBlocking {
        val author = MutableAuthor.new().apply {
            name = "Isaac Asimov"
            insert()
        }

        val book = MutableBook.new().apply {
            title = "Foundation"
            authorId = author.authorId
            insert()
        }

        MutableDude.new().apply {
            name = "Alice"
            insert()
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondHtml {
                body {
                    h1 { +"My Bookshelf" }
                    ul {
                        runBlocking {
                            for (book in Book) {
                                val author = book.author()
                                val borrower = book.currentBorrower()
                                li {
                                    +book.title
                                    i { +" by ${author.name}" }

                                    if (borrower != null) {
                                        +" - borrowed by ${borrower.name}"
                                    } else {
                                        +" - available"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // REST API endpoint: the mutable schema, so the write routes exist
        route("/api/example") {
            rest(MutableExampleDatabase.bookshelf)
        }
    }
}
```

### JS Frontend (Client.kt)

```kotlin
import com.republicate.skorm.ApiClient
import kotlinx.browser.window

// Same database definition, different processor — and read-only, this client only reads
val database = ExampleDatabase(ApiClient("${window.location.origin}/api"))

fun main() {
    window.onload = {
        database.initialize()
        database.initJoins()
        database.initRuntimeModel()

        // Same code as backend!
        document.querySelector(".lend-form")?.addEventListener("submit") { event ->
            event.preventDefault()
            GlobalScope.launch {
                val bookId = form.getAttribute("data-book_id")
                val book = Book.fetch(bookId) ?: error("Book not found")
                val dudeId = selectElement.value.toLong()

                book.lend(dudeId)  // Calls REST API transparently
                document.location?.reload()
            }
        }
    }
}
```

### The Magic

The same business logic code works on both JVM and JS:

```kotlin
// This code is identical on server and client:
val book = Book.fetch(bookId)
book?.let {
    val borrower = it.currentBorrower()
    it.lend(dudeId)
    it.restitute()
}
```

**On JVM:** `CoreProcessor` → JDBC → Database
**On JS:** `ApiClient` → HTTP → REST API → `CoreProcessor` → JDBC → Database

The `Processor` abstraction makes your code platform-agnostic!
