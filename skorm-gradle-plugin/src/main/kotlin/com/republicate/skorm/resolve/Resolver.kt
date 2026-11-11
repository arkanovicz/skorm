package com.republicate.skorm.resolve

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import com.republicate.kddl.FieldType
import com.republicate.skorm.KotlinTool
import com.republicate.skorm.SchemaInfo
import com.republicate.skorm.SkormException
import com.republicate.skorm.model.RMCompositeType
import com.republicate.skorm.model.RMDatabase
import com.republicate.skorm.model.RMEntityRef
import com.republicate.skorm.model.RMItem
import com.republicate.skorm.model.RMSimpleType
import com.republicate.skorm.schemaNamed
import com.republicate.skorm.sqlName
import com.republicate.skorm.tableNamed

/**
 * Derives the [ResolvedModel] from the kddl structure and the ksql attributes.
 *
 * Pure: no Gradle, no I/O. Every naming rule the templates used to apply lives here, once.
 * Where today's output is known wrong the derivation is kept faithful and marked `FAITHFUL`,
 * so the golden tests prove the move to the resolved model changed nothing; each is fixed
 * in its own commit, with its own golden diff.
 */
class Resolver(private val kotlin: KotlinTool = KotlinTool()) {

    /** what the artifacts on the import path say of their generated code: whose classes an external schema's are */
    private var imports: List<SchemaInfo> = emptyList()

    /** the kddl schema that owns an external schema, and generated its classes */
    private fun owner(schema: ASTSchema): SchemaInfo = imports.firstOrNull { schema.sqlName in it.schemas }
        ?: throw SkormException("schema ${schema.name} is imported, but nothing in skorm.imports generates it: " +
            "the artifact of the kddl schema that owns it ships ${SchemaInfo.DIR}/<name>.properties")

    /**
     * The names of one half. The read-only half names things as declared (`ExampleDatabase`, `Book`);
     * the mutable half prefixes every class with `Mutable`, and each of its classes extends its read-only
     * counterpart. Composite rows are read-only in both halves, so they always take the read-only names.
     */
    private inner class Naming(val mutable: Boolean) {
        // an imported kddl schema generated read-only has no mutable classes to name
        private fun prefix(schema: ASTSchema? = null) =
            if (mutable && !(schema != null && schema.external && owner(schema).readOnly)) "Mutable" else ""
        val readOnly: Naming get() = if (mutable) Naming(false) else this
        fun database(db: ASTDatabase) = "${prefix()}${kotlin.pascal(db.sqlName)}Database"
        /** the database class of an imported kddl schema, by its full name: it lives in its own package */
        fun database(info: SchemaInfo) = "${info.destPackage}.${if (mutable && !info.readOnly) "Mutable" else ""}${kotlin.pascal(sqlName(info.database))}Database"
        fun schema(schema: ASTSchema) = "${prefix(schema)}${kotlin.pascal(schema.sqlName)}Schema"
        fun schemaClass(schema: ASTSchema) =
            "${if (schema.external) database(owner(schema)) else database(schema.db)}.${schema(schema)}"
        fun entity(table: ASTTable) = "${prefix(table.schema)}${kotlin.pascal(table.sqlName)}"
        fun entityClass(table: ASTTable) = "${schemaClass(table.schema)}.${entity(table)}"
        fun implClass(table: ASTTable) = "${schemaClass(table.schema)}.${entity(table)}Impl"
    }

    /**
     * [readOnly]: generate the read-only half alone, for a build that never writes.
     * [imports]: the kddl schemas found on the import path. Nothing is generated for an external schema:
     * its classes are its owner's, named here where a link points at them.
     */
    fun resolve(database: ASTDatabase, attributes: RMDatabase?, readOnly: Boolean = false, imports: List<SchemaInfo> = emptyList()): ResolvedModel {
        this.imports = imports
        val mutable = if (readOnly) null else half(database, attributes, Naming(true), null)
        val resolved = half(database, attributes, Naming(false), mutable)
        resolved.joins.forEach { Collisions.checkAccessor(it.receiverClass, it.name, it.label) }
        resolved.attributes.forEach { Collisions.checkAccessor(it.receiverClass, it.name, "attribute") }
        Collisions.checkUnique(resolved.joins, resolved.attributes)
        Collisions.checkComposites(resolved.schemas, resolved.attributes)
        return resolved
    }

    private fun half(database: ASTDatabase, attributes: RMDatabase?, names: Naming, mutableTwin: ResolvedModel?): ResolvedModel {
        val own = database.schemas.values.filter { !it.external }
        // first: a missing import is best reported as such, not through the first link that needs it
        val imported = importedDatabases(database, names)
        val joins = own.flatMap { joins(it, names) }
        val queries = attributes?.schemas?.flatMap { rm ->
            val schema = database.schemaNamed(rm.name) ?: throw SkormException("ksql schema ${rm.name}: no such schema in the model")
            if (schema.external) throw SkormException("ksql schema ${rm.name} is imported: its attributes are declared by the kddl schema that owns it")
            val enums = kotlin.enumDecls(schema).map { it.name }.toSet()
            rm.items.filter { names.mutable || !it.action }
                .map { checkArguments(it, rm.name, enums); queryAttribute(schema, names, it) }
        } ?: emptyList()
        return ResolvedModel(
            name = database.sqlName,
            databaseClass = names.database(database),
            schemas = own.map { schema(it, names, joins, queries) },
            imports = imported,
            joins = joins,
            attributes = queries,
            mutable = names.mutable,
            base = if (names.mutable) names.readOnly.database(database) else null,
            mutableTwin = mutableTwin
        )
    }

    /** The databases of the kddl schemas this one imports itself: it holds them, and brings them up on its processor. */
    private fun importedDatabases(database: ASTDatabase, names: Naming): List<ImportedDatabase> = database.imports.map { path ->
        val base = path.substringAfterLast('/').substringBeforeLast('.')
        val info = imports.firstOrNull { it.base == base }
            ?: throw SkormException("import '$path': nothing in skorm.imports ships ${SchemaInfo.DIR}/$base.properties, which says where its generated classes are")
        if (sqlName(info.database) == database.sqlName) throw SkormException(
            "import '$path': its database is named ${info.database} like this one; two generated databases of one name cannot be told apart")
        val property = kotlin.camel(sqlName(info.database))
        database.schemas.values.firstOrNull { !it.external && kotlin.camel(it.sqlName) == property }?.let {
            throw SkormException("import '$path': its database ${info.database} is named like schema ${it.name}, both would be `$property` on the generated database")
        }
        ImportedDatabase(base, property, info.destPackage, names.database(info), mutable = names.mutable && !info.readOnly)
    }

    private fun schema(schema: ASTSchema, names: Naming, joins: List<JoinAttribute>, queries: List<QueryAttribute>): ResolvedSchema {
        val schemaClass = names.schemaClass(schema)
        return ResolvedSchema(
            name = schema.sqlName,
            className = names.schema(schema),
            objectName = kotlin.camel(schema.sqlName),
            base = if (names.mutable) names.readOnly.schemaClass(schema) else null,
            enums = kotlin.enumDecls(schema).map { EnumDecl(it.name, it.values) },
            entities = schema.tables.values.map { entity(it, names, joins, queries) },
            attributes = queries.filter { it.receiverClass == schemaClass }
        )
    }

    private fun entity(table: ASTTable, names: Naming, joins: List<JoinAttribute>, queries: List<QueryAttribute>): ResolvedEntity {
        val entityClass = names.entityClass(table)
        val own = table.fields.values.map { field -> field(table, field) }
        val inherited = generateSequence(table.parent) { it.parent }.toList().asReversed()
            .flatMap { ancestor -> ancestor.fields.values.map { field(ancestor, it) } }
        Collisions.checkHierarchy(table)
        return ResolvedEntity(
            tableName = table.sqlName,
            className = names.entity(table),
            implClass = "${names.entity(table)}Impl",
            objectName = kotlin.camel(table.sqlName),
            base = if (names.mutable) names.readOnly.entityClass(table) else null,
            key = (inherited + own).filter { it.primaryKey },
            fields = inherited + own,
            ownFields = own,
            parentClass = table.parent?.let { names.entityClass(it) },
            parentImplClass = table.parent?.let { names.implClass(it) },
            source = sourceOf(table),
            // a kind is a value, not an identifier: the label kddl gave it, the table's name as written
            kinds = descendants(table).map { it.name to names.entityClass(it) },
            kindValue = if (table.parent != null || table.children.isNotEmpty()) table.name else null,
            joins = joins.filter { it.receiverClass == entityClass },
            attributes = queries.filter { it.receiverClass == entityClass }
        )
    }

    private fun field(table: ASTTable, field: com.republicate.kddl.ASTField): ResolvedField {
        // a declared enum carries its values like an inline one: the runtime knows an enum by that spelling alone
        val rawType = when (val t = field.type) {
            is FieldType.NamedEnum -> FieldType.InlineEnum(t.enum.values).toString()
            else -> t.toString()
        }
        val name = kotlin.camel(field.sqlName)
        Collisions.checkField(table.name, name)
        // kddl has no way to name an imported enum; one only gets here as the type of a key a link copies
        (field.type as? FieldType.NamedEnum)?.let {
            if (it.enum.schema.external) throw SkormException(
                "${table.name}.${field.name}: typed by ${it.enum.schema.name}.${it.enum.name}, an enum of an imported schema, which is not supported yet")
        }
        // the key a subtype inherits stays a key of its rows
        val root = generateSequence(table) { it.parent }.last()
        return ResolvedField(
            name = name,
            rawType = rawType,
            kotlinType = kotlin.type(field),
            nullable = !field.nonNull,
            primaryKey = field.primaryKey,
            generated = (field.type as? FieldType.Primitive)?.isSerial == true,
            writable = !field.primaryKey && field !== root.kind,
            getter = kotlin.getter(field),
            enumClass = if (kotlin.isEnum(field.type)) kotlin.enumName(field) else null
        )
    }

    /** a table's key, inherited from its parent when it declares none */
    private fun key(table: ASTTable): Set<com.republicate.kddl.ASTField> =
        table.getPrimaryKey().ifEmpty { table.parent?.let { key(it) } ?: emptySet() }

    private fun descendants(table: ASTTable): List<ASTTable> =
        table.children.sortedBy { it.sqlName }.flatMap { listOf(it) + descendants(it) }

    /**
     * What a table with descendants is read from, so that a row comes back complete and as its kind:
     * the table (a view, below the root) LEFT JOINed with each descendant's base table on the key.
     * kddl names a subtype's own table `base_<name>`, its `<name>` being the joined view.
     */
    private fun sourceOf(table: ASTTable): String? {
        val below = descendants(table)
        if (below.isEmpty()) return null
        val key = key(table).joinToString(", ") { it.sqlName }
        return "${table.schema.sqlName}.${table.sqlName}" + below.joinToString("") {
            " LEFT JOIN ${it.schema.sqlName}.base_${it.sqlName} USING ($key)"
        }
    }

    // ---- navigations ----------------------------------------------------------------------

    private fun joins(schema: ASTSchema, names: Naming): List<JoinAttribute> =
        schema.tables.values.flatMap { table ->
            if (kotlin.isJoinTable(table)) manyToMany(table, names)
            else table.foreignKeys.flatMap { fk -> foreignKey(fk, names) }
        }

    /**
     * What a single-column link is called from the referencing side: the referenced table when the column
     * is named after that table's key (as kddl names an implicit column — `author_id`, or `code` for a
     * table keyed by `code`), the column itself otherwise (`donor`, `parent`), less a trailing `_<key>`
     * (`club_code` -> `club`).
     */
    private fun navigationName(column: String, towards: ASTTable): String {
        val key = towards.getPrimaryKey().singleOrNull()?.sqlName ?: return kotlin.attributeName(column)
        if (column == key) return kotlin.camel(towards.sqlName)
        // a `_<key>` suffix is the same noise as `_id`
        return kotlin.attributeName(column.removeSuffix("_$key"))
    }

    private fun foreignKey(fk: ASTForeignKey, names: Naming): List<JoinAttribute> {
        val fromClass = names.entityClass(fk.from)
        val towardsClass = names.entityClass(fk.towards)
        val column = fk.fields.first().sqlName
        val forwardName = when {
            fk.fields.size == 1 -> navigationName(column, fk.towards)
            kotlin.isUniqueFkDest(fk) -> kotlin.camel(fk.towards.sqlName)
            else -> kotlin.attributeName(column) + kotlin.pascal(fk.towards.sqlName)
        }
        val forward = JoinAttribute(
            label = "forward foreign key",
            ownerSchema = kotlin.camel(fk.from.schema.sqlName), ownerEntity = kotlin.camel(fk.from.sqlName),
            receiverClass = fromClass, name = forwardName, targetClass = towardsClass,
            nullable = !fk.nonNull, multiple = false,
            sql = kotlin.foreignKeyForwardQuery(fk, sourceOf(fk.towards)),
            params = fk.fields.map { it.sqlName }
        )
        if (!fk.bidirectional) return listOf(forward)
        val reverseBase =
            if (kotlin.isUniqueFkDest(fk)) kotlin.camel(fk.from.sqlName)
            else kotlin.attributeName(column) + kotlin.pascal(fk.from.sqlName)
        val reverse = JoinAttribute(
            label = "reverse foreign key",
            ownerSchema = kotlin.camel(fk.towards.schema.sqlName), ownerEntity = kotlin.camel(fk.towards.sqlName),
            receiverClass = towardsClass, name = kotlin.plural(reverseBase), targetClass = fromClass,
            nullable = false, multiple = true,
            sql = kotlin.foreignKeyReverseQuery(fk, sourceOf(fk.from)),
            params = fk.towards.getPrimaryKey().map { it.sqlName }
        )
        return listOf(forward, reverse)
    }

    private fun manyToMany(join: ASTTable, names: Naming): List<JoinAttribute> {
        val leftFk = join.foreignKeys[0]
        val rightFk = join.foreignKeys[1]
        val left = leftFk.towards
        val right = rightFk.towards
        fun classOf(table: ASTTable) = names.entityClass(table)
        // the collection on each side is named after the far side's column, or its table for a multi-column key
        val rightToLeftBase = if (leftFk.fields.size == 1) navigationName(leftFk.fields.first().sqlName, left) else kotlin.camel(left.sqlName)
        val leftToRightBase = if (rightFk.fields.size == 1) navigationName(rightFk.fields.first().sqlName, right) else kotlin.camel(right.sqlName)
        return listOf(
            JoinAttribute(
                label = "left to right n-n join",
                ownerSchema = kotlin.camel(left.schema.sqlName), ownerEntity = kotlin.camel(left.sqlName),
                receiverClass = classOf(left), name = kotlin.plural(leftToRightBase), targetClass = classOf(right),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, false, sourceOf(right)),
                params = left.getPrimaryKey().map { it.sqlName }
            ),
            JoinAttribute(
                label = "right to left n-n join",
                ownerSchema = kotlin.camel(right.schema.sqlName), ownerEntity = kotlin.camel(right.sqlName),
                receiverClass = classOf(right), name = kotlin.plural(rightToLeftBase), targetClass = classOf(left),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, true, sourceOf(left)),
                params = right.getPrimaryKey().map { it.sqlName }
            )
        )
    }

    // ---- ksql attributes --------------------------------------------------------------------

    private val simpleTypes = setOf("Boolean", "Int", "Long", "Float", "Double", "LocalTime", "LocalDate",
        "LocalDateTime", "Instant", "DateTimePeriod", "Char", "String", "Json", "Any?")

    /** the simple type an argument names: `Int*?` is a nullable list of `Int` */
    private fun elementType(declared: String) = declared.removeSuffix("?").removeSuffix("*")

    /** `Int*` is a `List<Int>` in Kotlin, `Int*?` a `List<Int>?` */
    private fun kotlinArgumentType(declared: String): String {
        val nullable = declared.endsWith("?")
        val type = declared.removeSuffix("?")
        val kotlin = if (type.endsWith("*")) "List<${type.dropLast(1)}>" else type
        return if (nullable) "$kotlin?" else kotlin
    }

    /** An argument's type is a simple type or an enum class the schema declares, as a list or not, nullable or not. */
    private fun checkArguments(item: RMItem, schemaName: String, enums: Set<String>) {
        for ((name, declared) in item.arguments ?: emptySet()) {
            val type = elementType(declared)
            if (type !in simpleTypes && type !in enums) throw SkormException(
                "attribute ${label(item)}: argument $name has type $type, " +
                    "neither a simple type nor an enum of schema $schemaName" +
                    (if (enums.isEmpty()) "" else " (${enums.joinToString(", ")})"))
        }
    }

    private fun label(item: RMItem) = "${item.receiver?.let { "$it." } ?: ""}${item.name}"

    /** the table a ksql entity type names, through the model: `main.Person`, `main.person`, `Main.Person` are one table */
    private fun entityTable(block: ASTSchema, item: RMItem, ref: RMEntityRef): ASTTable {
        val schema = ref.schema?.let {
            block.db.schemaNamed(it) ?: throw SkormException("attribute ${label(item)}: type $ref: no schema $it in the model")
        } ?: block
        return schema.tableNamed(ref.table)
            ?: throw SkormException("attribute ${label(item)}: type $ref: no table ${ref.table} in schema ${schema.sqlName}")
    }

    private fun receiver(schema: ASTSchema, name: String): ASTTable =
        schema.tableNamed(name) ?: throw SkormException("ksql receiver $name: no such table in schema ${schema.sqlName}")

    private fun queryAttribute(schema: ASTSchema, names: Naming, item: RMItem): QueryAttribute {
        val schemaName = schema.sqlName
        val receiver = item.receiver?.let { receiver(schema, it) }
        val schemaClass = names.schemaClass(schema)
        val type = item.type
        val composite = (type as? RMCompositeType)?.let { c ->
            CompositeClass(
                className = kotlin.pascal(item.name),
                parentClass = c.parent?.let { names.readOnly.implClass(entityTable(schema, item, it)) } ?: "Json.MutableObject",
                fields = c.fields.map { CompositeField(kotlin.camel(it.name), it.type, it.nullable) }
            )
        }
        val entityClass = (type as? RMSimpleType)?.entity?.let { names.entityClass(entityTable(schema, item, it)) }
        val isEntity = entityClass != null
        val isJsonObject = type?.name == "Json.Object"
        val q = if (item.nullable) "?" else ""

        var generics = ""
        var cast = ""
        val verb = when {
            // a block of statements is performed too: the runtime runs several statements in one transaction
            item.action -> "perform"
            else -> {
                when {
                    composite != null -> {
                        generics = "<${kotlin.capitalize(item.name)}$q>"
                        if (!item.multiple) cast = " as ${kotlin.capitalize(item.name)}$q"
                    }
                    isEntity -> generics = "<$entityClass$q>"
                    else -> generics = "<${type!!.name}$q>"
                }
                val rows = composite != null || isEntity || isJsonObject
                // a row is never null in a flow: `?*` only means something for the values of a column
                if (rows && item.nullable && item.multiple) throw SkormException("attribute ${item.name}: ?* qualifies a scalar, not rows")
                when {
                    item.multiple -> if (rows) "query" else "harvest"
                    rows -> "retrieve"
                    else -> "eval"
                }
            }
        }

        val itemClass: String?
        val factory: String?
        when {
            item.action -> { itemClass = null; factory = null }
            composite != null -> { itemClass = kotlin.pascal(item.name); factory = "::$itemClass" }
            isEntity -> { itemClass = entityClass; factory = "$itemClass::new" }
            else -> { itemClass = null; factory = null }
        }
        fun rows(cls: String) = if (item.multiple) "rowSetAttribute<$cls>" else if (item.nullable) "nullableRowAttribute<$cls>" else "rowAttribute<$cls>"
        val core = when {
            item.action -> "mutationAttribute"
            itemClass != null -> rows(itemClass)
            isJsonObject && item.multiple -> "rowSetAttribute<Json.MutableObject>"
            item.multiple -> "scalarSetAttribute<${type!!.name}$q>"
            isJsonObject -> if (item.nullable) "nullableRowAttribute<Json.MutableObject>" else "rowAttribute<Json.MutableObject>"
            else -> "scalarAttribute<${type!!.name}$q>"
        }
        val client = when {
            item.action -> "mutationAttribute"
            composite != null -> rows(itemClass!!)
            isEntity && item.multiple -> "rowSetAttribute<$itemClass>"
            isEntity -> if (item.nullable) "nullableInstanceAttribute<$itemClass>" else "instanceAttribute<$itemClass>"
            isJsonObject && item.multiple -> "rowSetAttribute<Json.MutableObject>"
            item.multiple -> "scalarSetAttribute<${type!!.name}$q>"
            isJsonObject -> "scalarAttribute<Json.Object$q>"
            else -> "scalarAttribute<${type!!.name}$q>"
        }

        return QueryAttribute(
            qualifiedName = "${item.receiver ?: schemaName}.${item.name}",
            name = item.name,
            schema = kotlin.camel(schemaName),
            receiverClass = receiver?.let { names.entityClass(it) } ?: schemaClass,
            registrationPath = receiver?.let { ".entity(\"${kotlin.camel(it.sqlName)}\").instanceAttributes" } ?: "",
            arguments = item.arguments?.map { (name, declared) -> name to kotlinArgumentType(declared) } ?: emptyList(),
            verb = verb, entityRows = isEntity, generics = generics, cast = cast,
            coreRegistration = core, clientRegistration = client, factory = factory,
            sql = item.sql ?: "",
            params = item.parameters.toList(),
            composite = composite,
            types = (item.arguments?.map { elementType(it.second) } ?: emptyList()) + listOfNotNull((type as? RMSimpleType)?.takeIf { !isEntity }?.name) +
                (composite?.fields?.map { it.type } ?: emptyList())
        )
    }
}
