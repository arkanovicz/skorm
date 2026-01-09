# Architecture Overview

Skorm is a multiplatform Kotlin ORM with a unique abstraction that allows the same client code to work seamlessly whether it's directly accessing a database (backend/JVM) or calling through a REST API (frontend/JS/WASM).

## Module Structure

1. skorm-common (multiplatform): Core abstractions

- Processor interface - the key abstraction
- Database, Schema, Entity, Instance classes
- Attribute types (ScalarAttribute, RowAttribute, RowSetAttribute, MutationAttribute)

2. skorm-core (multiplatform): CoreProcessor implementation

- Direct database query execution
- SQL statement generation
- restMode = false

3. skorm-jdbc (JVM only): JDBC connector

- Provides the JdbcConnector for database access

4. skorm-api-client (multiplatform): ApiClient implementation

- HTTP client using Ktor
- Calls REST endpoints
- restMode = true

5. skorm-api-server (JVM only): REST API server

- Ktor server endpoints that expose the database operations

6. skorm-gradle-plugin: Code generation

- Reads .kddl files (database structure)
- Reads .ksql files (custom queries)
- Generates platform-specific Kotlin classes

## The Key Abstraction

The Processor interface provides these methods:

- eval() - scalar queries
- retrieve() - single row
- query() - multiple rows (Sequence)
- perform() - mutations

Both CoreProcessor and ApiClient implement this interface, so client code like:

```
val book = Book.fetch(id)
book.title = "New Title"
book.update()
```
...works identically whether using direct DB access (JVM/CoreProcessor) or REST API (JS/ApiClient)!

## Bookshelf Example

- Server (Server.kt): ExampleDatabase(CoreProcessor(JdbcConnector())) - direct DB
- Client (Client.kt): ExampleDatabase(ApiClient("/api")) - REST calls
- Same operations work in both: Book.fetch(), book.insert(), book.lend(), etc.

  The abstraction is clean and elegant - the client code is truly unaware of the underlying implementation!
