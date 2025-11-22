# skorm

*Simple Kotlin Object Relational Mapping*

The nicest Kotlin multiplatform ORM around. Fully multiplatform. Coroutines-enabled.

## Concepts

*Five* main concepts:

+ Database
+ Schema
+ Entity, corresponding to a table or a view
+ Instance, corresponding to a single row in a table or a row set
+ Attribute, corresponding to a custom query, with five variants:
 
     + ScalarAttribute, returning Any?
     + RowAttribute, returning Instance?
     + RowSetAttribute, returning Sequence<Instance>
     + MutationAttribute, returning Long (either the number of modified rows, or the generated serial value)
     + TransactionAttribute, returning List<Long> (number of modified rows for each comprised mutation statement)

*Four* main methods in the lifecycle of database objects instances (along with transaction handling):

+ Instance.insert()
+ Entity.fetch(primaryKey)
+ Instance.update()
+ Instance.delete()

*Three* main customization points:

+ identifiers mapping (snake to camel, prefix/suffix removal, lowercase ...)
+ fields filtering (hide secret field, mark field as read-only, ...)
+ values filtering (transform timestamps

*Two* concrete database connectors, one for <abbr title="Java DataBase Connectivity">JDBC</abbr> and one for a service <abbr title="Application Programmable Interface">API</abbr>. More to come, hopefully.

*One* goal: maximum simplicity without conceeding anyting to extensibility.

*Zero* annotation. Zero <abbr title="Set Query Language">SQL</abbr> code fragmentation.

## Configuration

+ A <abbr title="Data definition Language">DDL</abbr> dialect, `kddl`, for database creation and versioning, as well as UML diagrams and Kotlin classes generation
+ A <abbr title="Data definition Language">DML</abbr> dialect for custom queries, with automatic undo handling and Kotlin extension functions

```
Database ⁎—— Schema ⁎—— Entity ⁎—— Instance
```

*Five* main verbs to deal with them:

+ `eval(name, params...)` which returns a scalar value
+ `retrieve(name, params...)` which returns a single row (plus `Entity.fetch(params...)` to get an instance by ID)
+ `query(name, params...)` which returns a rowset
+ `perform(name, params...)` for atomic mutations
+ `attempt(name, params...)` for transactions

*Many* features:
+ automatic generation of undo statements

## Example

Let's code a minimalistic backoffice webapp to keep track of the books I have and my friends borrow me.

#### **`bookshelf.ddl`:**
```
database bookshelf {

  schema books {
  
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
    borrowing -> book, dude
  }
}
```

Using the companion tool [`kddl`](https://github.com/arkanovicz/kddl) (command line, `gradle` or `mvn` plugin), I'll get a database SQL creation script and a plantuml diagram.

## Custom Queries (ksql format)

Beyond the basic CRUD operations, skorm allows you to define custom queries and mutations using the `ksql` format. These definitions generate type-safe Kotlin objects and extension functions.

#### **`bookshelf.ksql`:**
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
    attr foo: (dude_id: Long, borrowed: Int, borrowing: Int)* =
      SELECT dude_id,
        COUNT(restitution_date) borrowed,
        SUM(CASE WHEN borrowing_date IS NULL THEN 1 ELSE 0 END) borrowing
      FROM borrowing;
  }
}
```

### Attribute Syntax

**Declaration:**
- `attr [Entity.]name: ReturnType = SQL` - defines a query attribute
- `mut [Entity.]name[(params)] = SQL` - defines a mutation attribute

**Return Types:**
- `Type` - non-nullable scalar (Int, Long, String, Boolean, LocalDate, LocalDateTime, etc.)
- `Type?` - nullable scalar
- `(Entity, extra_field: Type, ...)` - composite object extending an entity with extra fields
- `(Entity, extra_field: Type, ...)?` - nullable composite object
- `(field: Type, ...)` - anonymous object with named fields
- `(field: Type, ...)?` - nullable anonymous object
- `(...)* ` - sequence (rowset) of objects

**Parameters:**
- SQL parameters are enclosed in curly braces: `{param_name}`
- For entity-level attributes, entity fields are automatically available as parameters
- Mutation parameters are declared in the signature: `mut Entity.action(param: Type)`

### Generated Code

From the above definitions, skorm generates:

```kotlin
// Schema-level function
suspend fun BookshelfSchema.booksCount(): Int

// Entity-level function returning composite object
suspend fun Book.currentBorrower(): CurrentBorrower?
// where CurrentBorrower extends Dude with borrowingDate property

// Mutation with parameter
suspend fun Book.lend(dude_id: Long): Long

// Mutation without parameter
suspend fun Book.restitute(): Long

// Function returning anonymous object
suspend fun Book.stats(): Stats
// where Stats has titleLength and borrowed properties

// Function returning sequence
suspend fun BookshelfSchema.foo(): Sequence<Foo>
// where Foo has dudeId, borrowed, and borrowing properties
```

All generated functions are coroutine-based (`suspend`) and type-safe, providing compile-time checking of parameters and return types.

