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

*Three* what ?

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

Using the companion tool `kddl` (command line, `gradle` or `mvn` plugin), I'll get this <a href="#">SQL script</a> (for Postgres in this example) and the following plantuml diagram:

<img src="#"/>

## Usage

### Gradle

### Maven

### Command Line

Happy Debian and Ubuntu users can install the native command line tools using `apt`:

`sudo apt install skorm-tools`

and for diagrams, you'll also need:

`sudo apt install graphviz plantuml`

DDL script generation:

`kddl -i bookshelf.ddl -f postgresql > bookshelf.sql`

DDL image generation:

`kddl -i bookshelf -f plantuml | plantuml -p -tsvg > bookshelf.svg`

## Performances


## Reference

See [the online documentation](https://skorm.republicate.com).

