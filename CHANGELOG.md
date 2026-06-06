# Changelog

All notable changes to Skorm are documented in this file.

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
