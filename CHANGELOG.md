# Changelog

All notable changes to Skorm are documented in this file.

## [0.21] - unreleased

### Fixed
- The statement splitter's regex escaped a double quote with a backslash, which JavaScript's `u`-flag regex rejects: `AttributeDefinition` threw at class initialisation on JS and wasm, so it had never worked there. Found by the first skorm-core tests, which run on every platform.
- An attribute found through inheritance executed at the calling holder's path (`/…/vip/addresses`), where the processor knows nothing; an attribute now remembers the holder it was registered on and executes at that path. REST paths are unchanged.
- The statement splitter behind every mutation (`AttributeDefinition`) cut a statement at any `;`, parentheses or not — so a PostgreSQL rule with several actions, `DO INSTEAD ( …; …; )`, which kddl emits for every inherited table, failed with `syntax error at end of input` when the creation script ran through `perform`. Parentheses are now a nesting state: a `;`, a quote or a `{param}` inside them is text or a parameter, never a statement end.
- The PostgreSQL enum-binding test skipped on *any* Testcontainers failure, indistinguishable from a machine without Docker — a transient failure once hid it from a full `check`. It now skips only when no Docker is present (no socket, no `DOCKER_HOST`) or `SKORM_SKIP_PG_TESTS` is set; a Docker that is present but unreachable fails the test with Testcontainers' diagnosis.
- The ksql parser printed syntax errors and went on with the recovered, truncated tree — a mistyped model quietly generated wrong code, and bookshelf's `countInGenre(genre: Genre)` only worked *through* that recovery, the grammar having no enum argument types. An argument may now be typed by an enum the model declares (`genre: Genre`), checked against the schema's enums with a message naming the attribute; and the first syntax error fails generation, naming the file, line and column.
- A ksql mutation declared as a block of statements — `mut x = { …; …; }`, meant to run as one transaction — had never lexed: the lexer entered query mode straight after `=`, where `{` can only open a parameter. It now enters the block mode that was already written for it, and the resolver hands the query parser the statements without the braces. The generated accessor is `perform`, which the runtime already executes in one transaction when there are several statements; the `attempt` verb, never implemented, is gone from the templates and the README. Bookshelf's `newBookBy(author_name, title)` inserts the author then the book in one transaction, and its test runs it.
- The generated database's singleton guard said "instance already crated".
- The generation task read its inputs through `project.file()` at execution time, which the configuration cache forbids; it now reads the file properties directly. The build enables the configuration cache (`gradle.properties`), after removing a `doFirst` in the plugin's own build that captured the script object and the TestKit debug mode the example test ran in, which cannot serialize the cache in-process. A consumer with the cache on gets it for generation too. `publishToMavenLocal` still discards the cache entry — Dokka 1.9.20's tasks are marked incompatible — until the Dokka V2 migration.
- Generated files that stopped being emitted — a dropped table, `client` flipped off — lingered in `build/generated-src` and got compiled; the output directory is now cleared before each generation.

### Changed
- **Table inheritance is generated** (kddl `table vip : person`): `Vip : Person`, `VipFields : PersonFields` declaring only its own columns, its entity registering every column its rows carry with the parent's key, and a companion bound to the parent entity so a Vip answers to `Person`'s navigations and attributes. Every entity class now takes its entity as a constructor parameter (`Book(entity: Entity = Companion)`), which is how a subtype binds its own companion. **Loading is polymorphic by default**: a table with descendants reads its table LEFT JOINed with each descendant's base table on the key (`Entity.source`), so `Person.browse()`, `Person.fetch()` and every navigation to a Person come back as the class the row's `kind` names — `Person.new(kind)` dispatches, and a hierarchy row without a `kind` column fails naming what's missing. A user's own ksql attribute keeps the SQL it writes. Two subtypes declaring the same column name are refused at generation, since `SELECT *` over the joins would silently return one. A subtype's base table is kddl's `base_<name>`, by convention.
- kddl 0.28 → 0.29: an insert through an inherited table's view returns its row — the rule's RETURNING moved to its last action — so the generated key comes back through JDBC as for a plain table. A new row of a hierarchy member knows its `kind` before the database applies it (`Vip()` says `vip`), instead of failing on the field until refetched. Table inheritance is verified end to end by `InheritanceTest`: a consumer generated and compiled by TestKit runs its own test on a Testcontainers PostgreSQL — polymorphic `browse` and `fetch`, a navigation to a hierarchy member, an inherited navigation asked of a subtype. kddl 0.29 also warns on every implicit primary key, implicit keys being deprecated: bookshelf's three tables now warn; declare their keys before kddl drops the generation.
- Runtime groundwork for table inheritance (kddl `table vip : person`): an `Entity` may name its parent entity, and a subtype's rows then answer to the parent's attributes — navigations, ksql attributes — through attribute lookup (`AttributeHolder.inherited`, walked before the parent holder; REST paths are unchanged, they follow the schema). An entity may read from a wider `source` than its table, so a hierarchy root can read its table joined with its descendants'. `RowFactory` is now a small interface rather than a function type, with `new(kind)`: both processors read a row's `kind` before building it, and an entity is its own factory — a root will build the subclass the kind names. Registrations take one factory parameter, defaulting to plain objects, in place of the entity-or-factory overloads.
- kddl 0.27 → 0.28: the inheritance discriminator is a real column, `kind`, typed by an enum of the hierarchy's table names and defaulting to the root's — where it used to be a `class varchar(30)` the SQL formatter added on its own, invisible to the model. skorm maps it like any enum column, `person.kind: PersonKind`, except that it is read-only: the child views' insert rules own its value. (Consumer builds resolve a locally published kddl through `mavenLocal()`, after Central, in the plugin repositories too — the plugin's own dependencies resolve there.)
- The code generator derives its model once. A `Resolver` turns the kddl structure and the ksql attributes into a resolved model — entities, fields, every navigation and every attribute as one row carrying its accessor *and* both registrations — and the seven templates became loops over it: they print, they derive nothing. Output is byte-identical to 0.20 except for indentation on n-n lines and blank lines where a `##` comment used to leak spaces, both verified whitespace-only against golden files. The known generation defects are reproduced faithfully and marked in the resolver, each to be fixed with its own golden diff. Naming collisions are now caught at generation time instead of at the consumer's compilation: a Kotlin keyword as a column or navigation name is backticked; a column or accessor that would override a member every row inherits from `Instance` (`size`, `entity`, `put`, `update`…) is refused naming the table and column; two accessors resolving to the same name on one receiver — a navigation and a ksql attribute, say — are refused. The inherited-member list is static, a workaround for Gradle 8.x whose embedded stdlib (2.0.21) lacks `kotlin.time.Instant` and so cannot reflect over `Instance`; the plugin's own tests recompute it reflectively, and Gradle 9 lets it go.
- Top-level typealiases for the generated classes, added in 0.20, are no longer generated: the generator provides the scoped names (`ExampleDatabase.BookshelfSchema.Book`) and an app aliases the ones it uses — which is where a name collision is actually decidable. Migration: add the typealiases you relied on to your own sources.

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
