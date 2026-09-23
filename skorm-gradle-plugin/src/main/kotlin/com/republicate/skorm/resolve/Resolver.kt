package com.republicate.skorm.resolve

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import com.republicate.kddl.FieldType
import com.republicate.skorm.KotlinTool
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
        return ResolvedModel(
            name = database.name,
            databaseClass = databaseClass,
            schemas = database.schemas.values.map { schema(it) },
            joins = database.schemas.values.flatMap { joins(it, databaseClass) },
            attributes = attributes?.schemas?.flatMap { rm ->
                rm.items.map { queryAttribute(databaseClass, rm.name, it) }
            } ?: emptyList()
        )
    }

    private fun schema(schema: ASTSchema) = ResolvedSchema(
        name = schema.name,
        className = "${kotlin.pascal(schema.name)}Schema",
        objectName = kotlin.camel(schema.name),
        enums = kotlin.enumDecls(schema).map { EnumDecl(it.name, it.values) },
        entities = schema.tables.values.map { entity(it) }
    )

    private fun entity(table: ASTTable) = ResolvedEntity(
        tableName = table.name,
        className = kotlin.pascal(table.name),
        objectName = kotlin.camel(table.name),
        hasPrimaryKey = table.getPrimaryKey().isNotEmpty(),
        fields = table.fields.values.map { field ->
            val rawType = field.type.toString()
            ResolvedField(
                name = kotlin.camel(field.name),
                rawType = rawType,
                kotlinType = kotlin.type(field),
                nullable = !field.nonNull,
                primaryKey = field.primaryKey,
                // FAITHFUL: `bigserial` is not recognised as generated
                generated = rawType == "serial",
                getter = kotlin.getter(field),
                enumClass = if (kotlin.isEnum(field.type)) kotlin.enumName(field) else null
            )
        }
    )

    // ---- navigations ----------------------------------------------------------------------

    private fun joins(schema: ASTSchema, databaseClass: String): List<JoinAttribute> {
        val schemaClass = "$databaseClass.${kotlin.pascal(schema.name)}Schema"
        val schemaObject = kotlin.camel(schema.name)
        return schema.tables.values.flatMap { table ->
            if (kotlin.isJoinTable(table)) manyToMany(table, databaseClass)
            else table.foreignKeys.flatMap { fk -> foreignKey(fk, schemaClass, schemaObject) }
        }
    }

    private fun foreignKey(fk: ASTForeignKey, schemaClass: String, schemaObject: String): List<JoinAttribute> {
        // FAITHFUL: both ends are named in the schema being iterated — wrong for a cross-schema link
        val fromClass = "$schemaClass.${kotlin.pascal(fk.from.name)}"
        val towardsClass = "$schemaClass.${kotlin.pascal(fk.towards.name)}"
        val column = fk.fields.first().name
        val forwardName = when {
            fk.fields.size == 1 -> kotlin.attributeName(column)
            kotlin.isUniqueFkDest(fk) -> kotlin.camel(fk.towards.name)
            else -> kotlin.attributeName(column) + kotlin.pascal(fk.towards.name)
        }
        val forward = JoinAttribute(
            label = "forward foreign key",
            ownerSchema = schemaObject, ownerEntity = kotlin.camel(fk.from.name),
            receiverClass = fromClass, name = forwardName, targetClass = towardsClass,
            nullable = !fk.nonNull, multiple = false,
            sql = kotlin.foreignKeyForwardQuery(fk),
            params = fk.fields.map { it.name }
        )
        if (!fk.bidirectional) return listOf(forward)
        val reverseBase =
            if (kotlin.isUniqueFkDest(fk)) kotlin.camel(fk.from.name)
            else kotlin.attributeName(column) + kotlin.pascal(fk.from.name)
        val reverse = JoinAttribute(
            label = "reverse foreign key",
            ownerSchema = schemaObject, ownerEntity = kotlin.camel(fk.towards.name),
            receiverClass = towardsClass, name = kotlin.plural(reverseBase), targetClass = fromClass,
            nullable = false, multiple = true,
            sql = kotlin.foreignKeyReverseQuery(fk),
            params = fk.towards.getPrimaryKey().map { it.name }
        )
        return listOf(forward, reverse)
    }

    private fun manyToMany(join: ASTTable, databaseClass: String): List<JoinAttribute> {
        val leftFk = join.foreignKeys[0]
        val rightFk = join.foreignKeys[1]
        val left = leftFk.towards
        val right = rightFk.towards
        fun classOf(table: ASTTable) = "$databaseClass.${kotlin.pascal(table.schema.name)}Schema.${kotlin.pascal(table.name)}"
        // the collection on each side is named after the far side's column, or its table for a multi-column key
        val rightToLeftBase = if (leftFk.fields.size == 1) kotlin.attributeName(leftFk.fields.first().name) else kotlin.camel(left.name)
        val leftToRightBase = if (rightFk.fields.size == 1) kotlin.attributeName(rightFk.fields.first().name) else kotlin.camel(right.name)
        return listOf(
            JoinAttribute(
                label = "left to right n-n join",
                ownerSchema = kotlin.camel(left.schema.name), ownerEntity = kotlin.camel(left.name),
                receiverClass = classOf(left), name = kotlin.plural(leftToRightBase), targetClass = classOf(right),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, false),
                params = left.getPrimaryKey().map { it.name }
            ),
            JoinAttribute(
                label = "right to left n-n join",
                ownerSchema = kotlin.camel(right.schema.name), ownerEntity = kotlin.camel(right.name),
                receiverClass = classOf(right), name = kotlin.plural(rightToLeftBase), targetClass = classOf(left),
                nullable = false, multiple = true,
                sql = kotlin.joinTableQuery(join, true),
                params = right.getPrimaryKey().map { it.name }
            )
        )
    }

    // ---- ksql attributes --------------------------------------------------------------------

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
            item.transaction -> "attempt"
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
            // FAITHFUL: a multiple entity attribute is registered as a single instance on the client
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
