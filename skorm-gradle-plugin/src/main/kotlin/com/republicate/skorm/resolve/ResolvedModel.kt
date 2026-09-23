package com.republicate.skorm.resolve

/**
 * Everything the templates print, derived once. A template iterates and prints; it derives nothing,
 * so an accessor and its registrations cannot disagree on a name, a type or a direction.
 */
class ResolvedModel(
    /** the database name as declared, `example` */
    val name: String,
    /** `ExampleDatabase` */
    val databaseClass: String,
    val schemas: List<ResolvedSchema>,
    /** foreign-key and many-to-many navigations, in emission order */
    val joins: List<JoinAttribute>,
    /** ksql attributes and mutations, in declaration order; empty without a ksql file */
    val attributes: List<QueryAttribute>
)

class ResolvedSchema(
    val name: String,
    /** `BookshelfSchema` */
    val className: String,
    /** `bookshelf` */
    val objectName: String,
    val enums: List<EnumDecl>,
    val entities: List<ResolvedEntity>
)

class EnumDecl(val name: String, val values: List<String>)

class ResolvedEntity(
    val tableName: String,
    /** `Book` */
    val className: String,
    /** `book` */
    val objectName: String,
    val hasPrimaryKey: Boolean,
    /** every column the entity's rows carry: inherited first, then its own */
    val fields: List<ResolvedField>,
    /** the columns declared on this table, which its class declares (inherited ones come from the superclass) */
    val ownFields: List<ResolvedField>,
    /** `ExampleDatabase.MainSchema.Person` for `table vip : person`, null for a table without a parent */
    val parentClass: String?,
    /**
     * What a table with descendants reads from, its descendants' base tables LEFT JOINed on the key:
     * `main.person LEFT JOIN main.base_vip USING (person_id)`. Null for a table without descendants.
     */
    val source: String?,
    /** kind value to subclass, for every descendant: `vip` → `ExampleDatabase.MainSchema.Vip` */
    val kinds: List<Pair<String, String>>,
    /** the value of `kind` for this table's own rows, when it belongs to a hierarchy: `vip` */
    val kindValue: String?
)

class ResolvedField(
    /** `title` — the mapped property name */
    val name: String,
    /** the kddl type as declared, e.g. `varchar(100)` */
    val rawType: String,
    /** `String`, `Int`, `Genre`… */
    val kotlinType: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val generated: Boolean,
    /** a `var` in the generated class: neither a key nor a hierarchy's `kind`, which the database maintains */
    val writable: Boolean,
    /** `getString`, `getInt`, `getBytes`… */
    val getter: String,
    /** the enum class to convert through, when the field is an enum */
    val enumClass: String?
) {
    /** the property name as written in Kotlin: backticked when it is a keyword */
    val identifier get() = Collisions.identifier(name)
}

/** One navigation: its accessor and both registrations. */
class JoinAttribute(
    /** printed as the comment above the accessor: `forward foreign key`… */
    val label: String,
    /** `bookshelf` — the schema object holding the owning entity */
    val ownerSchema: String,
    /** `book` — the owning entity's name, for `entity("book")` */
    val ownerEntity: String,
    /** `ExampleDatabase.BookshelfSchema.Book` — the accessor's receiver */
    val receiverClass: String,
    /** `tags` */
    val name: String,
    /** `ExampleDatabase.BookshelfSchema.Tag` — the row class returned and registered */
    val targetClass: String,
    val nullable: Boolean,
    val multiple: Boolean,
    /** the core registration's SQL */
    val sql: String,
    /** the client registration's parameter names */
    val params: List<String>
) {
    /** the name as written in Kotlin: backticked when it is a keyword */
    val identifier get() = Collisions.identifier(name)
    /** `Sequence<…Tag>`, `…Dude?` */
    val returnType get() = if (multiple) "Sequence<$targetClass>" else if (nullable) "$targetClass?" else targetClass
    val verb get() = if (multiple) "query" else "retrieve"
    val registration get() = if (multiple) "rowSetAttribute" else if (nullable) "nullableRowAttribute" else "rowAttribute"
}

/** One ksql attribute or mutation: its accessor, its registrations, and the composite class it may declare. */
class QueryAttribute(
    /** `Book.currentBorrower`, `bookshelf.booksCount` — printed in comments */
    val qualifiedName: String,
    val name: String,
    /** `bookshelf` */
    val schema: String,
    /** `ExampleDatabase.BookshelfSchema.Book`, or the schema class when the receiver is the schema */
    val receiverClass: String,
    /** `.entity("book").instanceAttributes`, empty for a schema-level attribute */
    val registrationPath: String,
    /** declared arguments, in order: name to Kotlin type */
    val arguments: List<Pair<String, String>>,
    /** `retrieve`, `query`, `eval`, `perform` */
    val verb: String,
    /** `<Genre?>`, empty for mutations */
    val generics: String,
    /** ` as CurrentBorrower?`, or empty */
    val cast: String,
    /** `rowSetAttribute<CurrentBorrower>`, `scalarAttribute<Int>`, `mutationAttribute`… */
    val coreRegistration: String,
    /** the client's counterpart, which does not always match core */
    val clientRegistration: String,
    /** `::CurrentBorrower`, `ExampleDatabase.BookshelfSchema.Dude::new`, or null when the row needs no factory */
    val factory: String?,
    val sql: String,
    /** the parameter names the SQL binds, in order */
    val params: List<String>,
    val composite: CompositeClass?
)

class CompositeClass(
    /** `CurrentBorrower` */
    val className: String,
    /** `ExampleDatabase.BookshelfSchema.Dude` or `Json.MutableObject` */
    val parentClass: String,
    val fields: List<CompositeField>
)

class CompositeField(val name: String, val type: String, val nullable: Boolean) {
    val identifier get() = Collisions.identifier(name)
}
