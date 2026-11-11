# Changelog

All notable changes to Skorm are documented in this file.

## [0.33] - unreleased

### Changed
- kddl 0.35: a model split over several files (`include 'shared.kddl'`) finds the included ones beside the including one. The path was looked up from the working directory, the project's in a Gradle build, so an include next to a model in `src/main/model` was not found. Schemas declared on both sides now merge

## [0.32] - 2026-11-08

### Fixed
- A parameter between brackets was dropped: the runtime parser took `[…]` for a SQL Server quoted identifier, so PostgreSQL's `ARRAY[{a}, {b}]` went out with its braces. Brackets are plain text now; `'` and `"` are the only quotes the parser knows
- A ksql attribute returning columns generates a row class named after the attribute, at package level, while entities live in their schema object: `attr Community.dude: (…)*` produced a second `Dude`, which the registration resolved to the composite and the accessor to the entity, and the accessor's cast failed at runtime. Generation now refuses a composite named like an entity, an enum or another composite, and says which

## [0.31] - 2026-11-06

### Added
- List arguments: `attr booksAmong(ids: Int*): Book*` generates a `List<Int>` parameter (`Int*?` a `List<Int>?`), and a `List` (or a Java array) bound through JDBC becomes a SQL array, the shape `WHERE book_id = ANY({ids})` wants. H2 and HSQLDB get a `createArrayOf` array typed from the elements; PostgreSQL gets an array literal bound untyped, so the column types it: an enum column and an empty list need no cast. A new vendor flag, `driver.arrays`, says which engines have arrays (PostgreSQL, H2, HSQLDB; discovery mode probes `createArrayOf`), and a list reaching an engine without them fails with a clear error instead of the driver's. Over REST a list argument is not carried yet, like a null

## [0.30] - 2026-11-06

### Fixed
- Bumped kddl to 0.34 (index on FK source was dropped)

## [0.29] - 2026-11-06

### Fixed
- A one-letter SQL parameter (`LIMIT {n}`) failed to lex ("token recognition error at: '{n}'"): the ksql lexer wanted two characters for a parameter name, and lowercase only, where an argument name allowed one. Both are now spelled alike

## [0.28] - 2026-11-04

### Added
- Nullable ksql arguments: `attr Community.activity(dude_id: Int?, limit: Int)` (a simple type or a declared enum, with `?`) generates a nullable Kotlin parameter. The null is bound untyped, so on PostgreSQL the SQL has to type it where the column cannot: `{dude_id}::int IS NULL`. Over REST a null argument is still dropped by the client

## [0.27] - 2026-11-04

### Fixed
- A `Json` value could not be written to a `json`/`jsonb` column: the kson object reached the driver as is ("Can't infer the SQL type to use for an instance of com.republicate.kson.Json$MutableArray" on PostgreSQL, a refused class on H2). It is now bound as its JSON text; PostgreSQL takes it untyped and the column types it, H2 stores it as a JSON string that the read filter already unwraps. The column reads back as a `Json`, on both engines

## [0.26] - 2026-11-04

### Fixed
- A plain `timestamp`, `date` or `time` passed through the JVM's zone, as a `java.sql.Timestamp`/`Date`/`Time`: a wall clock inside a DST gap (02:30 on the night Paris springs forward) came back as 03:30 and was stored as 03:30. Columns are now read with JDBC 4.2's `getObject(i, java.time.LocalDateTime/LocalDate/LocalTime.class)` and bound as `java.time` values. A `time` column was not usable at all: it was read as a `java.sql.Time` no getter accepts, and a `LocalTime` parameter reached the driver unconverted
- A schema-level ksql attribute returning rows (`attr everybody: Person*`) did not compile: the mutable schema overrides it, but the read-only one was not `open`

## [0.25] - 2026-11-03

### Changed
- **Breaking**: `timestamptz` maps to `kotlin.time.Instant` instead of `LocalDateTime`. PostgreSQL's driver returned it as wall-clock time in the JVM's zone; H2 and HSQLDB returned an `OffsetDateTime` no getter could convert
- **Breaking**: `timetz` is refused at generation (was `LocalTime`, offset dropped, and shifted to the JVM's zone on PostgreSQL); there is no multiplatform offset time yet
- **Breaking**: kddl 0.32 (now 0.33) normalizes type spellings, so `Field.type`, which keys the `filter.read.<type>` / `filter.write.<type>` filters, carries the standard name (`int` → `integer`, `long`/`biginteger` → `bigint`, `datetime` → `timestamp`, `clob` → `text`…)

### Added
- ksql `Instant`, for values, arguments and composite fields (`InstantAttribute`, served over REST)
- Typed keys on the generated entities: `Country.fetch(code: String)` beside the `vararg Any` one, and `MutableCountry.new(code: String)` for a declared (non-generated) key, the only way to give a new row its key — key fields have no setter, so a table with a natural key could not be inserted at all through the typed API. A hierarchy's rows are still born through `new()` / `new(kind)`

### Fixed
- Fetch, update and delete by an enum key failed on PostgreSQL: the key parameter was cast with the kddl spelling (`?::enum(...)`, or the declared enum's name). An enum key is now bound untyped, like any enum parameter since 0.19. For that the runtime `Field.type` of a declared enum field carries its values like an inline one, `enum('low','high')` instead of `level` (also the `filter.read/write.<type>` key)
- A `bigserial` key was not recognised as generated: it was sent on insert and never read back
- `jsonb` was refused at generation; it maps to `Json` like `json`
- Fetch, update and delete by key failed on PostgreSQL for a `tinyint`, `double` or `long` key, cast with the kddl name (`?::tinyint`): the driver properties now map kddl names to the engine's (`driver.cast_types`, `MetaInfos.castType`)
- `datetime`, `datetimetz`, `datetime_tz` and `timestamp_tz` were refused at generation (kddl 0.32)
- PostgreSQL DDL received `biginteger` and `datetime(n)` verbatim; HyperSQL DDL received `timestamptz` and `timetz`, which HSQLDB and H2 reject (kddl 0.32)

## [0.24] - 2026-11-01

### Changed
- Build: Gradle 9.4.1, Kotlin 2.4.21, Dokka 2.2.0, publishing through `com.vanniktech.maven.publish` (signing only on the `MavenCentral` tasks, no `-Prelease`)
- Dependencies: kddl 0.31, essential-kson 2.18, kotlinx-datetime 0.8.0, kotlinx-serialization 1.12.0, ktor 3.6.1, antlr-kotlin 1.0.14, slf4j 2.0.20, commons-lang3 3.21.0, commons-collections4 4.6.0
- The plugin's generation task is cacheable (Gradle 9 validation)

### Fixed
- `KotlinTool` threw the ANTLR 2 exception shaded in Gradle 8's Groovy instead of kddl's `SemanticException`; gone in Gradle 9

## [0.23] - 2026-10-08

### Changed
- Scalar flows: `attr cities: String*` yields a `Flow<String>` through a fifth verb, `harvest`; `Int?*` is a flow of nullable values; `?*` on rows is refused
- Composite fields may be nullable (`(name: String, born: LocalDate?)`) and a composite may have a single field
- kddl 0.30

## [0.22] - 2026-09-24

### Changed
- **Breaking**: many-row reads return a `Flow<T>` instead of a `Sequence<T>` (`Processor.query`, `RowSetAttribute`, `Entity.browse()`, generated accessors); `Entity.iterator()` is gone; `Processor.transaction`'s block is suspend. Connector calls run on `CoreProcessor`'s `blockingContext` (`Dispatchers.IO` on the JVM)
- JDBC many-row reads stream (`Connector.stream`, `jdbc.fetchSize` 1000 by default) on a held autocommit-off connection
- FK navigation names drop a trailing referenced key: `club_code -> club` gives `club()`
- **Breaking**: `initialize()` registers navigations and ksql attributes itself; `initJoins()` and `initRuntimeModel()` are gone
- A read-only database holds a read-only processor (`Database.processor` is a `ReadOnlyProcessor` view unless the database is mutable); `CoreProcessor.configure` refuses once initialized
- Pool's `maxStatements` enforced

### Fixed
- Model views (`schemas`, `entities`, `fields`, `attributes`) are read-only at runtime, reflection included
- Nested blocking twins on another thread lost the ambient transaction
- `JdbcConnector(DataSource)` was unusable
- The statement cache was unbounded; it is bounded by `maxStatements` (256 by default), LRU
- A transaction's statements, and every mutation's shared statement, were never closed or returned to the pool
- The connection pool handed out a busy connection at `max`; it now waits up to `timeout` (30 s)

## [0.21] - 2026-09-24

### Changed
- **Breaking**: read-only and mutable halves at every level: `Instance` is a read-only interface, `Database`/`Schema`/`Entity` the read-only objects, `MutableDatabase`/`MutableSchema`/`MutableEntity`/`MutableInstance` the ones with `perform` and the writes; generated `ExampleDatabase` and `MutableExampleDatabase`, per table the interfaces `Book` and `MutableBook`. Migration: `Book()` becomes `MutableBook.new()`, `BookFields` is gone, an app that writes constructs the mutable database; raw loading is behind `@SkormInternalApi`. `skorm { readOnly.set(true) }` generates the read-only half alone
- Blocking twins: `@JvmName("tags") fun tagsBlocking()` beside every read accessor, for reflection-driven callers; joins the rendering coroutine's transaction
- Navigations and ksql attributes are members of the row interface or schema class, no extensions; `skormJoins.kt` is no longer generated
- Table inheritance (kddl `table vip : person`): `Vip : Person`, polymorphic loading by the `kind` column
- The generator derives a resolved model once (`Resolver`), output pinned by golden files; naming collisions refused at generation time
- Top-level typealiases (0.20) are no longer generated

### Fixed
- The statement splitter cut at any `;`, parentheses included, and its regex never worked on JS or wasm
- A ksql block mutation (`mut x = { …; …; }`) never lexed; `attempt` is gone
- A ksql syntax error went on with a truncated tree; it now fails generation with file, line and column; an argument may be typed by a model enum
- An attribute found through inheritance executed at the wrong holder's path
- The PostgreSQL enum-binding test skipped on any Testcontainers failure
- The generation task was configuration-cache incompatible; stale generated files no longer linger

## [0.20] - 2026-09-21

### Fixed
- Many-to-many joins were broken end to end core-side and absent client-side
- Client-side FK parameter sets were swapped, fatal whenever the FK column is not named like the target PK
- A nullable forward FK generated a non-nullable accessor
- `tinyint`, `smallint`, `smallinteger`, `biginteger` failed with `unsupported type`
- A composite forward FK's attribute name diverged between the three templates

### Added
- A generated field interface per entity (`BookFields`)
- Top-level aliases for the generated classes

### Changed
- **Breaking**: kddl 0.27, reverse navigation follows traversal intent: `book *-- author` exposes both ways, `borrowing -> book` only `Borrowing.book()`. Migration: `*-->` → `*--`, `-->` → `--` wherever both navigations are wanted
- **Breaking**: plugin configuration `structure` → `model`, `runtimeModel` → `attributes`; one task `generateSkormCode` registers its outputs on the Kotlin source sets (delete the `srcDir`, `dependsOn`, `mustRunAfter` lines); output under `build/generated-src/{common,core,client}`; emission follows the project's targets, `core`/`client` force it
- Bookshelf example: explicit keys, every link kind, a `tag` many-to-many, no generation wiring

## [0.19] - 2026-06-10

### Fixed
- Entity-composite attributes registered the base entity core-side (`ClassCastException`)
- `Instance.putRawFields` dropped every non-entity column
- Entity mutations with several arguments bound them positionally against SQL order; they bind by name
- Client composite registration diverged from core (`multible` typo, missing factory)
- Binding a `String` to a PostgreSQL native enum failed in operator contexts; string parameters bind untyped on engines flagged `pedanticCasts`
- Enum-typed query parameters bind as their string constant and compile

### Changed
- `dialect` is mandatory and validated (`postgresql` or `hypersql`)
- kddl 0.24
- Bookshelf example: explicit `dialect`, a `genre` enum, a multi-argument mutation, runtime tests

## [0.18] - 2026-06-09

### Fixed
- A nullable to-one FK emitted `nullableRowAttribute` without importing it
- An entity-composite attribute registered its parent class unqualified
- A multiple composite-without-parent attribute registered as single (`multible` typo)
- The reverse FK query filtered the wrong column for a named FK

### Changed
- Bookshelf example exercises the above (nullable `donor` FK, `Author.catalog`)

## [0.17] - 2026-06-09

### Fixed
- `char(n)` failed generation with "unsupported type: char"

### Changed
- Kotlin 2.4.0; essential-kson 2.14, ktor 3.5.0, coroutines 1.11.0, serialization 1.11.0, kotlin-logging 8, evo-inflector 2.0, JUnit 6.1

### Notes
- `numeric`/`decimal`/`money` still map to `Double`

## [0.16] - 2026-06-06

### Added
- Ambient transactions: `database.transaction(schema) { ... }`; every skorm operation in the block joins it, nested blocks on the same database join the enclosing one. Not on REST-mode databases

### Fixed
- Attributes dispatched through a transaction failed with "attribute not found"
- Two concurrent transactions could share a connection

### Changed
- kddl 0.23; `skorm-common` depends on `kotlinx-coroutines-core`

## [0.15] - 2026-05-21

### Changed
- kddl 0.21 (`mysql-connector-j` back to 8.4.0, protobuf 4.x breaks some AGP release tasks)

## [0.14] - 2026-05-12

### Changed
- kddl 0.19: `KotlinTool.isEnum(FieldType)`, `enumDecls(schema)` replace the string forms
- Schema-level enums emit one shared Kotlin enum class; inline enums stay per field

### Fixed
- `JdbcConnector(url, user, password)` dropped the login
- `useJsonType` was set from UUID usage

## [0.13] - 2026-05-05

### Fixed
- `skorm-api-server` is actually published

## [0.12] - 2026-01-30

### Fixed
- Nullable scalar attributes not being registered (missing `addAttribute` call for nullable types)
- Scalar `eval` returning exception instead of null for empty result sets

### Notes
- Nullable scalar attributes return `null` for both empty result sets and NULL column values.
  Use `retrieve` if you need to distinguish "no row" from "row with NULL value".

## [0.11] - 2026-01-26

### Added
- Automatic json/jsonb column parsing via column type metadata in QueryResult
- SQL array support: java.sql.Array automatically converted to List<?>
- Stock `parseJson` value filter for JSON types (handles String, ByteArray, H2's double-encoding)
- Default read filters for `json`, `jsonb`, and `JSON` types

### Changed
- QueryResult now includes `types` array from JDBC metadata
- Value filters applied to ALL columns (including computed ones in custom queries), not just entity fields

### Fixed
- `@Suppress("UNCHECKED_CAST")` added to generated `fetch()` and `browse()` methods

## [0.10] - 2026-01-24

### Changed
- Upgrade Ktor to 3.4.0

## [0.9] - 2026-01-23

### Fixed

- Fix callGenericGetter missing instance parameter
- Fix fetch() throwing exception instead of returning null when entity not found
- Fix eval() returning row array instead of scalar value
- Fix getLastInsertId: convert property name to DB column name, strip quotes
- Map serial pseudo-types to actual SQL types for parameter casting
- Fix update() not persisting: include primary key in dirty field params
- Fix nullable entity attribute template
- Fix query parameter extraction using wrong string (qry vs raw) in QueryDefinition.parse()
- Fix templates to use nullableRowAttribute/retrieve for Json.Object return types instead of scalarAttribute/eval

## [0.8] - 2025-12-31

### Added
- Enum alias support in code generation
- Validation for missing primary keys and foreign keys during code generation
- Tests for enum generation with alias support

### Changed
- Drop redundant `Enum` suffix from generated enum class names
- Configurable `dialect` property in gradle plugin for DDL generation (postgresql/hsqldb)

### Fixed
- UPDATE dirty fields parameters (was only including PK, now includes SET clause params + PK)
- Enum code generation: pass field object not field.type to enumValues()

## [0.7] - 2025-12-03

### Added
- Composite objects mapping support
- Factory methods review for complex types

### Fixed
- Result row factory for composite types in code generation
- Camel/snake case conversion bug
- Code generation for complex type without parent entity

## [0.6] - 2025-09-11

### Added
- Support for Kotlin UUIDs in JDBC driver

### Changed
- Remove `schema_query` property - schema is now set programmatically on the Connection object

### Fixed
- Plugin tests
- Bookshelf example

## [0.5] - 2025-07-13

### Added
- JSON type support
- UUID type support
- Datetime/timestamp type support
- Subkeys processing
- Close method for processors and database object
- Casting to primary key parameters in fetch/update/delete statements
- Json.Object attribute output support

### Changed
- Values filtering and identifiers mapping refactoring
- KDDL 0.13 integration
- Points SCM to GitHub

### Fixed
- Bigint mapping
- Init order for identifiers mapping default value
- JDBC metaInfo
- Templatized field creation
- Objects generation template

## [0.4] - 2025-07-08

### Added
- Examples as part of plugin functional tests
- Missing types in plugin

### Changed
- Build refactoring: move from buildSrc to includeBuild

### Fixed
- N-N joins naming
- NPE when no config provided
- Enum getters case
- Enums fields handling
- Camel/snake problem
- JS tests

## [0.2] - 2022-10-19

### Added
- Initial release
- Kotlin Multiplatform support (JVM, JS, Native)
- KDDL schema-first code generation
- Gradle plugin for code generation
- JDBC connector
- REST API server (skorm-api-server)
- REST API client (skorm-api-client)
- Basic value filters for date/time types
- Instance factories
- Dirty field tracking
- Lazy sequences for query results
