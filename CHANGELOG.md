# Changelog

All notable changes to Skorm are documented in this file.

## [0.22] - unreleased

### Changed
- **FK navigation names**: a link column ending with the referenced key drops it, as `_id` already did: `club_code -> club` gives `club()`, not `clubCode()` beside the `clubCode` column property. Role-named columns (`donor`) keep their name.
- **One initialization call.** `initialize()` now registers the navigations and the ksql attributes itself; `initJoins()` and `initRuntimeModel()` are gone. They were separate only because the registrations are generated into the platform source sets, which common code cannot call: the generated `initialize()` now ends with a `registerAttributes()` that the core and client outputs provide, an `expect`/`actual` pair in a multiplatform consumer and a plain call in a `kotlin("jvm")` one. An app that forgot one of the two got "attribute not found" at its first navigation. **Breaking**: delete the two calls.

## [0.21] - 2026-09-24

### Changed
- **Read-only and mutable halves, at every level.** Runtime: `Instance` is an interface — a read-only map of the row's fields with the kson getters — backed by `InstanceImpl` (`Json.Object`) or `MutableInstanceImpl` (`Json.MutableObject`, dirty tracking, the typed `put`); a read-only row has no `put` at all. `Database`, `Schema`, `Entity` are the read-only objects; `MutableDatabase`, `MutableSchema`, `MutableEntity`, `MutableInstance` are the interfaces of a mutable database's objects, the only ones with `perform` and the writes; a read-only holder refuses to register a mutation. One `CoreProcessor(connector, readConnector = connector)` serves a store's two databases: attributes are registered by path *and* mutability, the read-only database reads on the read connector (`core.read.<tag>`, a SELECT-only role being the actual guarantee) and joins the mutable one's ambient transaction. Generated code: `ExampleDatabase` and `MutableExampleDatabase : ExampleDatabase`, `BookshelfSchema` and `MutableBookshelfSchema : BookshelfSchema`, and per table two *interfaces*, `Book : Instance` and `MutableBook : Book, MutableInstance`, whose companion objects are the entities (`Book.browse()`, `MutableBook.new()`); interfaces let a hierarchy and mutability inherit side by side (`MutableVip : Vip, MutablePerson`), `BookImpl`/`MutableBookImpl` being the storage behind them. Constructing a `MutableExampleDatabase` also constructs its read-only sibling (`readOnly`, `ExampleDatabase.instance`) over the same processor; `initJoins()`/`initRuntimeModel()` register both halves. `skorm { readOnly.set(true) }` generates the read-only half alone. **Breaking**: `Book()` becomes `MutableBook.new()`; `BookFields` is gone (`Book` is the contract); an app that writes constructs the mutable database; row bounds through the runtime are `Row` (`Map<String, Any?>`); raw loading (`putRawFields`…) is behind `@SkormInternalApi`; `Instance.refresh()` no longer leaves the row dirty.
- **Blocking twins**: beside every read accessor, `@JvmName("tags") fun tagsBlocking(): List<Tag>` on the impl class — what a reflection-driven caller (a template engine) reaches as `tags()`; Kotlin code calls the suspend accessor or `blocking { }`. It runs on `Database.blockingContext` and joins the transaction of the coroutine that is rendering (`transaction { }` installs a JVM thread companion). On JS and wasm it throws; native blocks but joins no transaction.
- **Navigations and ksql attributes are members**, declared in the row interface or the schema class — no extension functions, no import, visible to reflection, inherited by a subtype. `skormJoins.kt` is no longer generated; `skormModel.kt` keeps the composite row classes.
- **Table inheritance** (kddl `table vip : person`, kddl 0.27 → 0.29): `Vip : Person`, the subtype registering every column with the parent's key and answering to the parent's navigations and attributes (`AttributeHolder.inherited`; REST paths unchanged). Loading is polymorphic by default: a table with descendants reads its table LEFT JOINed with each descendant's base table (`Entity.source`), and `browse`, `fetch` and navigations come back as the class the row's `kind` names — `kind` being a real enum column since kddl 0.28, read-only, preset on a new mutable row; a hierarchy row without `kind` fails naming it. Two subtypes declaring one column name are refused at generation. Verified end to end by `InheritanceTest` on a Testcontainers PostgreSQL.
- **The generator derives its model once**: a `Resolver` turns kddl and ksql into a resolved model — entities, navigations, attributes, each row carrying its accessor *and* both registrations — and the templates are loops over it. Output pinned by golden files (`GoldenOutputTest`). Naming collisions are caught at generation time: keywords backticked, columns or accessors overriding an inherited row member refused, duplicate accessors refused. `RowFactory` is an interface with `new(kind)`; registrations take one factory parameter, defaulting to plain objects. Consumer builds resolve a locally published kddl through `mavenLocal()`.
- Top-level typealiases (0.20) are no longer generated: the generator provides scoped names, apps alias what they use.

### Fixed
- The statement splitter behind every mutation cut at any `;`, parentheses or not — every PostgreSQL rule with several actions (which kddl emits for inherited tables) broke `perform("create")`; parentheses are now a nesting state. Its regex escaped a double quote, which JavaScript rejects: it had never worked on JS or wasm.
- A ksql mutation declared as a block of statements (`mut x = { …; …; }`) had never lexed; it now runs as one transaction through `perform`. The unimplemented `attempt` verb is gone.
- The ksql parser went on after a syntax error with a truncated tree; the first error now fails generation with file, line and column. An argument may be typed by an enum the model declares (`genre: Genre`), checked against the schema.
- An attribute found through inheritance executed at the calling holder's path; it executes at the holder it was registered on.
- The PostgreSQL enum-binding test skipped on *any* Testcontainers failure; it skips only without Docker or with `SKORM_SKIP_PG_TESTS`.
- The generation task read its inputs at execution time in a way the configuration cache forbids; the build enables the cache, and a consumer with it on gets it for generation too (`publishToMavenLocal` still discards its entry, Dokka 1.9.20). Stale generated files no longer linger: the output directory is cleared before each generation.
- The generated singleton guard said "instance already crated".

## [0.20] - 2026-09-21

### Fixed
- Many-to-many joins were broken end to end: the core registration mirrored the accessors, naming the attribute after the *other* end (`Book.tags()` looked up `tags`, core registered `books` on `book`), pairing it with the opposite query direction and with the row type of the end it did not return. The three views now derive the attribute name the same way, from the far-side FK column.
- Many-to-many joins were not declared client-side at all — the join-table branch of the client template was empty, so every `*-*` accessor failed over REST.
- Client-side FK parameter sets were each other's: the forward attribute declared the target's PK columns and the reverse one the FK's own columns, while a parameter is looked up *by name inside the receiving instance*. Invisible whenever the FK column is named like the target PK (`author_id`), fatal otherwise (`donor --> dude?` asked a book for `dude_id`). This is the client-side half of the reverse-query fix shipped for core in 0.18.
- A nullable forward FK generated a non-nullable accessor (`Book.donor(): Dude` for `donor --> dude?`) while both registrations used `nullableRowAttribute`, so a row without the FK failed the cast instead of returning null.
- Code generation failed with `unsupported type` on `tinyint`, `smallint`, `smallinteger` and `biginteger`: kddl keeps the spelling a model used, and the type mapper only knew `byte`, `short` and `bigint`. Since kddl 0.26 documents `tinyint`/`smallint` as the real names and `byte`/`short` as aliases, a model written against current kddl would hit it. Every spelling kddl's lexer accepts is now mapped.
- The core join template derived a multi-column forward FK's attribute name without the unique-destination test the accessor and client templates apply, so the three names diverged for composite FKs.

### Added
- A generated field interface per entity (`BookFields`), implemented by the entity class. It gives a non-suspend view of a row the whole field contract for one `by row` clause, with real getters on the delegate — what a reflection-driven template engine needs — and it is a cleaner contract for consumers to program against than a nested class. Blocking accessors will hang off it once the resolved model lands.
- Top-level aliases for the generated entity, field-interface and enum classes (`typealias Book = ExampleDatabase.BookshelfSchema.Book`), emitted next to them in `skormObjects.kt`. Consumers were writing these by hand — the bookshelf example twice, once per platform. A simple name shared by several schemas, or clashing with the database class, gets no alias.

### Changed
- **kddl 0.24 → 0.27, and reverse navigation now follows traversal intent — breaking.** In kddl 0.27 a chevron pointing at the one side of a link means "no collection on the other side" (`book *-- author` exposes both `Book.author()` and `Author.books()`; `borrowing -> book` only `Borrowing.book()`), carried by `ASTForeignKey.bidirectional`. The three join templates now emit the reverse accessor and both its registrations only for a bidirectional link — together, so they cannot disagree. Every existing `-->` / `->` link therefore loses its collection; where it is wanted, migrate per kddl's note (`*-->` → `*--`, and `-->` → `--` in a field link). A many-to-many is unaffected: always both ways. kddl 0.27 also turns a syntax error into a failure instead of a silent recovery with a truncated tree, and rejects `a -- b` between two tables, which used to parse and create no link at all.
- **Plugin configuration, breaking.** `structure` is renamed `model` and `runtimeModel` is renamed `attributes`: kddl's own Gradle and Maven plugins already call the .kddl file `model`, so skorm was renaming its upstream's concept for nothing, and `runtimeModel` read as "the model, at runtime" rather than the queries and mutations declared over it — which skorm elsewhere calls attributes (`attr`/`mut`, `Attribute`, `instanceAttributes`, `AttributeDefinition`). `dialect` deliberately keeps its name against kddl's `format`, whose values include `PLANTUML` and `KDDL`; skorm accepts only the two that really are SQL dialects. The four generation tasks are now one, `generateSkormCode`, and the plugin registers the generated directories on the consumer's Kotlin source sets — so a build declares no `srcDir`, no `dependsOn` and no `mustRunAfter` for code generation; Gradle derives the ordering from the producing task. Output moved from `build/generated-src/{commonMain,jvmMain,jsMain}` to `build/generated-src/{common,core,client}/{kotlin,resources}`, named by role: `jvmMain` meaning "server" was a conflation. What gets emitted now follows the project's Kotlin targets — server registrations and the creation script for a JVM target, REST client registrations for JS/wasm/native — instead of everything unconditionally, which is how a JVM-only consumer ended up with a `skormModelClient.kt` it had no target and no dependency for. `core` and `client` can be set explicitly to force either on or off, so a project with no JS target can still emit client code for another build to consume. The wiring works in any project layout — including the plugin declared at the root with Kotlin applied only in a subproject — and the plugin takes no dependency on the Kotlin Gradle plugin. `dialect` is now required only when the creation script is actually generated. Migration: delete the `skorm.dest*File` settings, the generated-source `srcDir` lines and the generation `dependsOn`/`mustRunAfter` blocks.
- Bookshelf example: its tables declare their keys (`*book_id serial`…) — kddl 0.29 deprecates the implicit ones and warns on each; the generated classes now list the key first. `book *-- author` (both ways) beside one-way `donor --> dude?` and `borrowing -> …` links, with a runtime check that `Author.books()` works and that neither the `Dude.books()` accessor nor its registration exists — which fails with `[Author, Dude, Tag]` if the templates stop honouring the intent. Its README relationships section is rewritten from verified output: it documented `->` and `-->` as different relationships, though the lexer read them as the same token sequence, and gave a comma-separated example the grammar never accepted.
- Bookshelf example: all generation wiring deleted from its build (three `srcDir` lines, six `dependsOn`, three `mustRunAfter`, and a `processResources` copy); a `tag` table joined `book *-* tag`, so the many-to-many path finally has a model to run on, plus runtime coverage of both join directions and of the nullable `donor` accessor. Its hand-written typealiases are gone — being in the generated package, they would now clash with the generated ones.

## [0.19] - 2026-06-10

### Fixed
- Entity-composite attributes (`attr X.foo: (Entity, field)…`) registered the base entity core-side while the accessor casts to the generated subclass (`query<Roster>`), throwing `ClassCastException`. Now registers the subclass.
- `Instance.putRawFields` silently dropped every non-entity column — the elvis branch was a discarded lambda (`?: { putRawValue(...) }`), never invoked — so a composite's extra field (e.g. `borrowing_date` in `(Dude, borrowing_date)`) never populated. Fixed at source; the generated per-composite `putRawFields` override (a broken workaround) is gone.
- Entity-level mutations with more than one argument bound named params positionally, so when the SQL param order (SET before WHERE) differed from the signature order, params crossed (e.g. `{kind}` received another argument's value). Such accessors now pass arguments by name (`mapOf(...)`); 0/1-argument accessors stay positional.
- Client (REST) model registration diverged from core for composites: a `multible` typo dropped the multiple qualifier, and no-parent composites registered the base `Json.MutableObject` (no factory) while the accessor casts to the generated subclass — a client-side `ClassCastException`. Both composite branches now register the subclass + factory, matching core.
- Binding a `String` to a PostgreSQL native enum column failed in operator contexts (`WHERE enum = ?` → `operator does not exist: enum = character varying`): PG won't apply the implicit `varchar→enum` cast for operators, only for assignment. The JDBC connector now binds string parameters untyped (`setObject(…, Types.OTHER)`) for engines flagged `pedanticCasts` (PostgreSQL), letting the server infer the type in both contexts. Verified against real Postgres via Testcontainers (skipped when Docker is absent or `SKORM_SKIP_PG_TESTS` is set).
- Enum-typed query parameters: a generated Kotlin enum passed as an attribute argument now binds as its string constant (was handed to JDBC unconverted), and generated accessors import the nested enum classes so enum-typed parameters compile.

### Changed
- The SQL `dialect` is now mandatory and validated — `postgresql` or `hypersql` (kddl's `Format` names); an unset or unknown dialect fails with a clear message instead of silently defaulting to HyperSQL.
- Bumped kddl 0.23 → 0.24 (HyperSQL enums render as CHECKed varchar domains; the `postgres` dialect alias is dropped).
- Bookshelf example: explicit `dialect`, `currentBorrower`'s composite field aligned to `LocalDate`, a multi-argument `returnFrom` mut, a `book.genre` enum with a genre-filtered `countInGenre` query (the same declaration is a native enum on Postgres, a CHECKed varchar domain on H2), and a runtime test covering entity-composite population, multi-param mut binding, and enum round-trip/filtering.

## [0.18] - 2026-06-09

### Fixed
- Code generation for several to-one / composite shapes that bookshelf never exercised:
  - A nullable to-one FK emitted `nullableRowAttribute` in the core and client join code without importing it (the join templates imported only `rowAttribute`/`rowSetAttribute`). Both now use a wildcard import.
  - An entity-composite attribute (`attr X.foo: (Entity, field)…`) registered the parent class unqualified (`<Registration>`, `::Registration`) in the core runtime model; now fully qualified (`Db.Schema.Registration`, `…::new`) like the plain-entity case.
  - A multiple composite-without-parent attribute (`attr X.foo: (a, b)*`) was registered as `rowAttribute` instead of `rowSetAttribute` (a `multible` typo dropped the multiple qualifier), failing at runtime with "attribute cannot have a null result".
  - Reverse FK query filtered the wrong column for a named FK: it paired the target-PK name as the column and the FK-column name as the parameter (`book.dude_id = {donor}`), which only happens to read correctly when the FK column is named like the target PK (`<table>_id`). Now `book.donor = {dude_id}`.

### Changed
- Bookshelf example enriched (nullable `donor` FK on `book`, multiple composite `Author.catalog` attribute) so the build actually exercises the above paths; added a `foreignKeyReverseQuery` unit test for a named FK column.

## [0.17] - 2026-06-09

### Fixed
- `char(n)` columns failed Kotlin code generation with "unsupported type: char". kddl parses `char(n)` fine; `KotlinTool` now maps it to `String` like `varchar`.

### Changed
- Bumped Kotlin 2.3.0 → 2.4.0 (atomicfu plugin 0.29 → 0.33).
- Bumped dependencies: essential-kson 2.12 → 2.14, ktor 3.4.0 → 3.5.0, kotlinx-coroutines 1.10.2 → 1.11.0, kotlinx-serialization 1.9.0 → 1.11.0, kotlin-logging 7 → 8, evo-inflector 1.3 → 2.0, commons-lang3 3.18 → 3.20, h2 2.3.232 → 2.4.240, antlr-kotlin 1.0.5 → 1.0.10, mockito 5.18 → 5.23, JUnit Jupiter/Platform 5.13/1.13 → 6.1, versions plugin 0.52 → 0.54.

### Notes
- `numeric`/`decimal`/`money` still map to `Double`; exact `BigDecimal` mapping (via essential-kson's multiplatform bignum) is deferred to a future release pending an essential-kson `toBigDecimal` precision fix.

## [0.16] - 2026-06-06

### Added
- Ambient (coroutine-scoped) transactions: `database.transaction(schema) { ... }` (plus a no-arg overload for single-schema databases) runs the block with the transaction carried in the coroutine context; all skorm operations against that database — entity `insert`/`update`/`delete`/`fetch`/`browse`, schema/entity attributes, raw `eval`/`perform` — join it transparently. Commit on normal exit, rollback (and rethrow) on exception. Nested blocks on the same database join the enclosing transaction; transactions on distinct databases nest independently. Caveats: the single transaction connection is not safe for parallel fan-out inside the block; lazy `Sequence` results must be iterated inside the block; not supported on REST-mode databases (`ApiClient.begin` is still unimplemented).
- Kotlin integration tests in skorm-jdbc (H2) covering the transaction semantics.

### Fixed
- `CoreProcessorTransaction` was a blank `CoreProcessor` around the transaction connector — empty query registry, identity mappers, default filters — so any registered attribute dispatched through a transaction failed with "attribute not found". It now shares the parent processor's state.
- Two concurrent transactions on the same schema could be handed the same physical connection (the pool's busy flag was only set around individual JDBC calls), interleaving their commits/rollbacks. The transaction connection is now held exclusively from `begin` to terminal `commit`/`rollback`.

### Changed
- Bumped kddl 0.21 → 0.23.
- `skorm-common` now depends on `kotlinx-coroutines-core`.

## [0.15] - 2026-05-21

### Changed
- Bumped kddl 0.19 → 0.21. Pulls in kddl 0.21's `mysql-connector-j` downgrade to 8.4.0 (last release on protobuf-java 3.x). Avoids forcing protobuf 4.x onto consumer buildscript classpaths, which broke AGP's Tink-based release tasks with `NoSuchMethodError` on `Keyset.makeExtensionsImmutable`. Also picks up kddl 0.20's inheritance-INSERT fix (`COALESCE(NEW.col, <default>)` in view INSERT rules so omitted DEFAULT columns actually take their default instead of NULL).

## [0.14] - 2026-05-12

### Changed
- Bumped kddl 0.18 → 0.19. `ASTField.type` is now a sealed `FieldType` (`Primitive`/`InlineEnum`/`NamedEnum`); `KotlinTool` migrated to pattern-match on it, and `isEnum(String)` is replaced by `isEnum(FieldType)`.
- Named enums declared at schema level (`enum status(...)`) now emit a single shared Kotlin enum class per `ASTEnum`, named after the enum (PascalCase). Inline `enum('a','b')` field declarations keep per-field naming. `KotlinTool.enums(schema)` is replaced by `enumDecls(schema): List<EnumDecl>` (deduped by `ASTEnum` identity).

### Fixed
- `JdbcConnector(url, user, password, ...)` constructor was storing the login under a dead `"user"` config key while `getLogin()` reads `"login"`, so credentials passed via the constructor were silently dropped. Constructor now writes to `"login"`. Connections worked anyway against credential-less DBs (H2 in-memory), which is why the bug went unnoticed.
- Bookshelf example `application.conf` was using `user`/`pass` keys (also silently dropped); fixed to `login`/`password`.
- `useJsonType` Velocity context flag was being set from `"uuid"` membership (copy/paste bug) — the `import com.republicate.kson.Json` line in generated objects was therefore emitted whenever a model used a UUID and never when it actually used a JSON type. Now correctly checks for `json`/`jsonb`.

## [0.13] - 2026-05-05

### Fixed
- `skorm-api-server` artifact now actually published. The JVM module had `maven-publish` applied but no `MavenPublication` declared, so `publishToMavenLocal`/`publishToSonatype` were silent no-ops. Added explicit publication and `withSourcesJar()` to mirror `skorm-jdbc`.

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
