package com.republicate.skorm.resolve

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import com.republicate.kddl.FieldType
import com.republicate.skorm.KotlinTool
import com.republicate.skorm.SkormException
import com.republicate.skorm.model.RMCompositeType
import com.republicate.skorm.model.RMDatabase
import com.republicate.skorm.model.RMItem
import com.republicate.skorm.model.RMSimpleType

/**
 * Derives the [ResolvedModel] from the kddl structure and the ksql attributes.
 *
 * Pure: no Gradle, no I/O. Every naming rule the templates used to apply lives here, once.
 * Where today's output is known wrong the derivation is kept faithful and marked `FAITHFUL`,
 * so the golden tests prove the move to the resolved model changed nothing; each is fixed
 * in its own commit, with its own golden diff.
 */
class Resolver(private val kotlin: KotlinTool = KotlinTool()) {

    fun resolve(database: ASTDatabase, attributes: RMDatabase?): ResolvedModel {
        val databaseClass = "${kotlin.pascal(database.name)}Database"
        val joins = database.schemas.values.flatMap { joins(it, databaseClass) }
        val queries = attributes?.schemas?.flatMap { rm ->
            val schema = database.schemas[rm.name] ?: throw SkormException("ksql schema ${rm.name}: no such schema in the model")
            val enums = kotlin.enumDecls(schema).map { it.name }.toSet()
            rm.items.map { checkArguments(it, rm.name, enums); queryAttribute(databaseClass, rm.name, it) }
        } ?: emptyList()
        joins.forEach { Collisions.checkAccessor(it.receiverClass, it.name, it.label) }
        queries.forEach { Collisions.checkAccessor(it.receiverClass, it.name, "attribute") }
        Collisions.checkUnique(joins, queries)
        return ResolvedModel(
            name = database.name,
            databaseClass = databaseClass,
            schemas = database.schemas.values.map { schema(it, databaseClass) },
            joins = joins,
            attributes = queries
        )
    }

    private fun schema(schema: ASTSchema, databaseClass: String) = ResolvedSchema(
        name = schema.name,
        className = "${kotlin.pascal(schema.name)}Schema",
        objectName = kotlin.camel(schema.name),
        enums = kotlin.enumDecls(schema).map { EnumDecl(it.name, it.values) },
        entities = schema.tables.values.map { entity(it, databaseClass) }
    )

    private fun entity(table: ASTTable, databaseClass: String): ResolvedEntity {
        val own = table.fields.values.map { field -> field(table, field) }
        val inherited = generateSequence(table.parent) { it.parent }.toList().asReversed()
            .flatMap { ancestor -> ancestor.fields.values.map { field(ancestor, it) } }
        Collisions.checkHierarchy(table)
        return ResolvedEntity(
            tableName = table.name,
            className = kotlin.pascal(table.name),
            objectName = kotlin.camel(table.name),
            hasPrimaryKey = key(table).isNotEmpty(),
            fields = inherited + own,
            ownFields = own,
            parentClass = table.parent?.let { classOf(it, databaseClass) },
            source = sourceOf(table),
            kinds = descendants(table).map { it.name to classOf(it, databaseClass) },
            kindValue = if (table.parent != null || table.children.isNotEmpty()) table.name else null
        )
    }

    private fun field(table: ASTTable, field: com.republicate.kddl.ASTField): ResolvedField {
        val rawType = field.type.toString()
        val name = kotlin.camel(field.name)
        Collisions.checkField(table.name, name)
        // the key a subtype inherits stays a key of its rows
        val root = generateSequence(table) { it.parent }.last()
        return ResolvedField(
            name = name,
            rawType = rawType,
            kotlinType = kotlin.type(field),
            nullable = !field.nonNull,
            primaryKey = field.primaryKey,
            // FAITHFUL: `bigserial` is not recognised as generated
            generated = rawType == "serial",
            writable = !field.primaryKey && field !== root.kind,
            getter = kotlin.getter(field),
            enumClass = if (kotlin.isEnum(field.type)) kotlin.enumName(field) else null
        )
    }

    /** a table's key, inherited from its parent when it declares none */
    private fun key(table: ASTTable): Set<com.republicate.kddl.ASTField> =
        table.getPrimaryKey().ifEmpty { table.parent?.let { key(it) } ?: emptySet() }

    private fun descendants(table: ASTTable): List<ASTTable> =
        table.children.sortedBy { it.name }.flatMap { listOf(it) + descendants(it) }

    /**
     * What a table with descendants is read from, so that a row comes back complete and as its kind:
     * the table (a view, below the root) LEFT JOINed with each descendant's base table on the key.
     * kddl names a subtype's own table `base_<name>`, its `<name>` being the joined view.
     */
    private fun sourceOf(table: ASTTable): String? {
        val below = descendants(table)
        if (below.isEmpty()) return null
        val key = key(table).joinToString(", ") { it.name }
        return "${table.schema.name}.${table.name}" + below.joinToString("") {
            " LEFT JOIN ${it.schema.name}.base_${it.name} USING ($key)"
        }
    }

    // ---- navigations ----------------------------------------------------------------------

    private fun joins(schema: ASTSchema, databaseClass: String): List<JoinAttribute> =
        schema.tables.values.flatMap { table ->
            if (kotlin.isJoinTable(table)) manyToMany(table, databaseClass)
            else table.foreignKeys.flatMap { fk -> foreignKey(fk, databaseClass) }
        }

    /**
     * What a single-column link is called from the referencing side: the referenced table when the column
     * is named after that table's key (as kddl names an implicit column — `author_id`, or `code` for a
     * table keyed by `code`), the column itself otherwise (`donor`, `parent`).
     */
    private fun navigationName(column: String, towards: ASTTable): String {
        val key = towards.getPrimaryKey().singleOrNull()?.name
        return if (key != null && column == key) kotlin.camel(towards.name) else kotlin.attributeName(column)
    }

    private fun classOf(table: ASTTable, databaseClass: String) =
        "$databaseClass.${kotlin.pascal(table.schema.name)}Schema.${kotlin.pascal(table.name)}"

    private fun foreignKey(fk: ASTForeignKey, databaseClass: String): List<JoinAttribute> {
        val fromClass = classOf(fk.from, databaseClass)
        val towardsClass = classOf(fk.towards, databaseClass)
        val column = fk.fields.first().name
        val forwardName = when {
            fk.fields.size == 1 -> navigationName(column, fk.towards)
            kotlin.isUniqueFkDest(fk) -> kotlin.camel(fk.towards.name)
            else -> kotlin.attributeName(column) + kotlin.pascal(fk.towards.name)
        }
        val forward = JoinAttribute(
            label = "forward foreign key",
            ownerSchema = kotlin.camel(fk.from.schema.name), ownerEntity = kotlin.camel(fk.from.name),
            receiverClass = fromClass, name = forwardName, targetClass = towardsClass,
            nullable = !fk.nonNull, multiple = false,
            sql = kotlin.foreignKeyForwardQuery(fk, sourceOf(fk.towards)),
            params = fk.fields.map { it.name }
        )
        if (!fk.bidirectional) return listOf(forward)
        val reverseBase =
            if (kotlin.isUniqueFkDest(fk)) kotlin.camel(fk.from.name)
            else kotlin.attributeName(column) + kotlin.pascal(fk.from.name)
        val reverse = JoinAttribute(
            label = "reverse foreign key",
            ownerSchema = kotlin.camel(fk.towards.schema.name), ownerEntity = kotlin.camel(fk.towards.name),
            receiverClass = towardsClass, name = kotlin.plural(reverseBase), targetClass = fromClass,
            nullable = false, multiple = true,
            sql = kotlin.foreignKeyReverseQuery(fk, sourceOf(fk.from)),
            params = fk.towards.getPrimaryKey().map { it.name }
        )
        return listOf(forward, reverse)
    }

    private fun manyToMany(join: ASTTable, databaseClass: String): List<JoinAttribute> {
        val leftFk = join.foreignKeys[0]
        val rightFk = join.foreignKeys[1]
        val left = leftFk.towards
        val right = rightFk.towards
        fun classOf(table: ASTTable) = classOf(table, databaseClass)
        // the collection on each side is named after the far side's column, or its table for a multi-column key
        val rightToLeftBase = if (leftFk.fields.size == 1) navigationName(leftFk.fields.first().name, left) else kotlin.camel(left.name)
        val leftToRightBase = if (rightFk.fields.size == 1) navigationName(rightFk.fields.first().name, right) else kotlin.camel(right.name)
        return listOf(
            JoinAttribute(
                label = "left to right n-n join",
                ownerSchema = kotlin.camel(left.schema.name), ownerEntity = kotlin.camel(left.name),
                receiverClass = classOf(left), name = kotlin.plural(leftToRightBase), targetClass = classOf(right),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, false, sourceOf(right)),
                params = left.getPrimaryKey().map { it.name }
            ),
            JoinAttribute(
                label = "right to left n-n join",
                ownerSchema = kotlin.camel(right.schema.name), ownerEntity = kotlin.camel(right.name),
                receiverClass = classOf(right), name = kotlin.plural(rightToLeftBase), targetClass = classOf(left),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, true, sourceOf(left)),
                params = right.getPrimaryKey().map { it.name }
            )
        )
    }

    // ---- ksql attributes --------------------------------------------------------------------

    private val simpleTypes = setOf("Boolean", "Int", "Long", "Float", "Double", "LocalTime", "LocalDate",
        "LocalDateTime", "DateTimePeriod", "Char", "String", "Json", "Any?")

    /** An argument's type is a simple type or an enum class the schema declares. */
    private fun checkArguments(item: RMItem, schemaName: String, enums: Set<String>) {
        for ((name, type) in item.arguments ?: emptySet()) {
            if (type !in simpleTypes && type !in enums) throw SkormException(
                "attribute ${item.receiver?.let { "$it." } ?: ""}${item.name}: argument $name has type $type, " +
                    "neither a simple type nor an enum of schema $schemaName" +
                    (if (enums.isEmpty()) "" else " (${enums.joinToString(", ")})"))
        }
    }

    private fun queryAttribute(databaseClass: String, schemaName: String, item: RMItem): QueryAttribute {
        val schemaClass = "$databaseClass.${kotlin.pascal(schemaName)}Schema"
        val type = item.type
        val composite = (type as? RMCompositeType)?.let { c ->
            CompositeClass(
                className = kotlin.pascal(item.name),
                parentClass = c.parent?.let { "$schemaClass.${kotlin.pascal(it)}" } ?: "Json.MutableObject",
                // FAITHFUL: a composite field is never nullable (the runtime model carries no such flag)
                fields = c.fields.map { CompositeField(kotlin.camel(it.name), it.type, false) }
            )
        }
        val isEntity = (type as? RMSimpleType)?.isEntity == true
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
                    isEntity -> generics = "<$schemaClass.${kotlin.capitalize(type!!.name)}$q>"
                    else -> generics = "<${type!!.name}$q>"
                }
                when {
                    item.multiple -> "query"
                    composite != null && ((type as RMCompositeType).parent != null || type.fields.size > 1)
                        || isEntity || isJsonObject -> "retrieve"
                    else -> "eval"
                }
            }
        }

        val itemClass: String?
        val factory: String?
        when {
            item.action -> { itemClass = null; factory = null }
            composite != null -> { itemClass = kotlin.pascal(item.name); factory = "::$itemClass" }
            isEntity -> { itemClass = "$schemaClass.${kotlin.pascal(type!!.name)}"; factory = "$itemClass::new" }
            else -> { itemClass = null; factory = null }
        }
        fun rows(cls: String) = if (item.multiple) "rowSetAttribute<$cls>" else if (item.nullable) "nullableRowAttribute<$cls>" else "rowAttribute<$cls>"
        val core = when {
            item.action -> "mutationAttribute"
            itemClass != null -> rows(itemClass)
            item.multiple -> "rowSetAttribute<Json.MutableObject>"
            isJsonObject -> if (item.nullable) "nullableRowAttribute<Json.MutableObject>" else "rowAttribute<Json.MutableObject>"
            else -> "scalarAttribute<${type!!.name}$q>"
        }
        val client = when {
            item.action -> "mutationAttribute"
            composite != null -> rows(itemClass!!)
            isEntity && item.multiple -> "rowSetAttribute<$itemClass>"
            isEntity -> if (item.nullable) "nullableInstanceAttribute<$itemClass>" else "instanceAttribute<$itemClass>"
            item.multiple -> "scalarAttribute<${type!!.name}>"
            isJsonObject -> "scalarAttribute<Json.Object$q>"
            else -> "scalarAttribute<${type!!.name}$q>"
        }

        return QueryAttribute(
            qualifiedName = "${item.receiver ?: schemaName}.${item.name}",
            name = item.name,
            schema = kotlin.camel(schemaName),
            receiverClass = item.receiver?.let { "$schemaClass.${kotlin.capitalize(it)}" } ?: schemaClass,
            registrationPath = item.receiver?.let { ".entity(\"${kotlin.snake(it)}\").instanceAttributes" } ?: "",
            arguments = item.arguments?.toList() ?: emptyList(),
            verb = verb, generics = generics, cast = cast,
            coreRegistration = core, clientRegistration = client, factory = factory,
            sql = item.sql ?: "",
            params = item.parameters.toList(),
            composite = composite
        )
    }
}
