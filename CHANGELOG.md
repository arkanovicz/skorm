# Changelog

All notable changes to Skorm are documented in this file.

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
