package com.republicate.skorm

import com.republicate.kddl.ASTEnum
import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import com.republicate.kddl.FieldType
import groovyjarjarantlr.SemanticException
import org.atteo.evo.inflector.English
import java.util.*

@Suppress("unused")
class KotlinTool {
    companion object {
        private val enInflector = English(English.MODE.ENGLISH_CLASSICAL)
    }

    /** Kotlin type name for a field's value. */
    fun type(field: ASTField): String = when (val t = field.type) {
        is FieldType.NamedEnum -> pascal(t.enum.name)
        is FieldType.InlineEnum -> field.alias ?: pascal(field.name)
        is FieldType.Primitive -> kotlinType(t.base)
    }

    private fun kotlinType(base: String): String = when (base.lowercase(Locale.ROOT)) {
        "boolean" -> "Boolean"
        "char", "text", "varchar", "clob" -> "String" // TODO streams for "text" and "clob"
        "date" -> "LocalDate"
        "timestamp" -> "LocalDateTime"
        "timestamptz" -> "LocalDateTime" // for now TODO
        "time" -> "LocalTime"
        "timetz" -> "LocalTime" // for now TODO
        "byte" -> "Byte"
        "short" -> "Short"
        "int", "integer", "serial" -> "Int"
        "long", "bigint", "bigserial" -> "Long"
        "float" -> "Float"
        "double" -> "Double"
        "money", "numeric", "decimal" -> "Double" // TODO
        "blob" -> "ByteArray"
        "uuid" -> "Uuid"
        "binary", "varbinary" -> "ByteArray" // TODO streams
        "json" -> "Json"
        else -> throw SemanticException("unsupported type: $base")
    }

    /** Velocity helper: getter method name for a field, based on its Kotlin type. */
    fun getter(field: ASTField): String = when (val t = field.type) {
        is FieldType.NamedEnum, is FieldType.InlineEnum -> "getString"
        is FieldType.Primitive -> if (t.base.equals("blob", ignoreCase = true)) "getBytes" else "get${kotlinType(t.base)}"
    }

    /** Enum class declarations to emit for a schema:
     *  - one per [ASTEnum] referenced by [FieldType.NamedEnum] fields (deduped by identity)
     *  - one per [FieldType.InlineEnum] field (still per-field — collision policy revisited in a future kddl milestone)
     */
    fun enumDecls(schema: ASTSchema): List<EnumDecl> {
        val named = LinkedHashSet<ASTEnum>()
        val inline = mutableListOf<EnumDecl>()
        for (table in schema.tables.values) {
            for (field in table.fields.values) {
                when (val t = field.type) {
                    is FieldType.NamedEnum -> named.add(t.enum)
                    is FieldType.InlineEnum -> inline.add(EnumDecl(field.alias ?: pascal(field.name), t.values))
                    is FieldType.Primitive -> {}
                }
            }
        }
        return named.map { EnumDecl(pascal(it.name), it.values) } + inline
    }

    /** Enum class name to reference from the getter/setter of an enum-typed [field]. */
    fun enumName(field: ASTField): String = when (val t = field.type) {
        is FieldType.NamedEnum -> pascal(t.enum.name)
        is FieldType.InlineEnum -> field.alias ?: pascal(field.name)
        is FieldType.Primitive -> throw IllegalArgumentException("not an enum field: ${field.name}")
    }

    fun isEnum(type: FieldType) = type is FieldType.NamedEnum || type is FieldType.InlineEnum

    data class EnumDecl(val name: String, val values: List<String>)

    fun camel(identifier: String) = IdentifiersMapping.snakeToCamel(identifier)

    fun snake(identifier: String) = IdentifiersMapping.camelToSnake(identifier)

    fun pascal(identifier: String) = IdentifiersMapping.snakeToPascal(identifier)

//    fun propertyType(property: GeneratePropertiesCodeTask.ObjectProperty): String {
//        return when (property.type) {
//            SCALAR -> "Any?"
//            ROW -> "${pascal(property.entity!!)}${if (property.nullable) "?" else ""}"
//            ROWSET -> "Sequence<${property.entity?.let {pascal(it)} ?: "Instance"}>"
//        }
//    }

    fun plural(str: String): String = enInflector.getPlural(str)

    fun foreignKeyForwardQuery(fk: ASTForeignKey): String {
        val targetPk = fk.towards.getPrimaryKey().also {
            if (it.isEmpty()) throw IllegalStateException("Foreign key target table '${fk.towards.name}' has no primary key")
            if (it.size != fk.fields.size) throw IllegalStateException("Foreign key column count (${fk.fields.size}) doesn't match target primary key size (${it.size}) for FK to '${fk.towards.name}'")
        }
        return "SELECT * FROM ${fk.towards.schema.name}.${fk.towards.name} WHERE ${
            targetPk.zip(fk.fields).joinToString(" AND ") {
                "${fk.towards.name}.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun foreignKeyReverseQuery(fk: ASTForeignKey): String {
        val targetPk = fk.towards.getPrimaryKey().also {
            if (it.isEmpty()) throw IllegalStateException("Foreign key target table '${fk.towards.name}' has no primary key")
            if (fk.fields.size != it.size) throw IllegalStateException("Foreign key column count (${fk.fields.size}) doesn't match target primary key size (${it.size}) for FK from '${fk.from.name}'")
        }
        return "SELECT * FROM ${fk.from.schema.name}.${fk.from.name} WHERE ${
            fk.fields.zip(targetPk).joinToString(" AND ") {
                "${fk.from.name}.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun joinTableQuery(join: ASTTable, reverse: Boolean = false): String {
        join.foreignKeys.also {
            if (it.isEmpty()) throw IllegalStateException("Join table '${join.name}' has no foreign keys")
            if (it.size < 2) throw IllegalStateException("Join table '${join.name}' needs at least 2 foreign keys, found ${it.size}")
        }
        val fromFk = if (reverse) join.foreignKeys.last() else join.foreignKeys.first()
        val towardsFk = if (reverse) join.foreignKeys.first() else join.foreignKeys.last()
        val from = fromFk.towards
        val towards = towardsFk.towards
        val joinTable = fromFk.from
        assert(joinTable == towardsFk.from)
        val towardsPk = towards.getPrimaryKey().also {
            if (it.isEmpty()) throw IllegalStateException("Join target table '${towards.name}' has no primary key")
        }
        val fromPk = from.getPrimaryKey().also {
            if (it.isEmpty()) throw IllegalStateException("Join source table '${from.name}' has no primary key")
        }
        return "SELECT towards_table.* FROM ${joinTable.schema.name}.${joinTable.name} AS join_table JOIN ${towards.schema.name}.${towards.name} AS towards_table ON ${
            towardsPk.zip(towardsFk.fields).joinToString(" AND ") {
                "towards_table.${it.first.name} = join_table.${it.second.name}"
            }
        } WHERE ${
            fromFk.fields.zip(fromPk).joinToString(" AND ") {
                "join_table.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun fieldNames(fields: Set<ASTField>) = fields.joinToString(",") { "\"${it.name}\"" }

    fun names(fields: Set<String>) = fields.joinToString(",") { "\"$it\"" }

    // deprecated
    // fun arguments(fields: Set<String>) = fields.joinToString(",") { "${it}: Any?" }

    fun typedArguments(fields: Set<Pair<String, String?>>) = fields.joinToString(" ,") {
        "${it.first}: ${it.second ?: "Any?"}"
    }

    fun values(fields: Set<Pair<String, String?>>) = fields.joinToString(",") { it.first }

    fun capitalize(str: String) = str.replaceFirstChar { it.uppercase() }

    fun decapitalize(str: String) = str.replaceFirstChar { it.lowercase() }

    // only consider single-field keys
    fun isJoinTable(table: ASTTable) =
        table.foreignKeys.size == 2 && table.foreignKeys.flatMap { it.fields }.toSet() == table.fields.values.toSet()

    fun attributeName(fieldName: String) =
        camel(fieldName.replace(Regex("_id$|Id$"), ""))

    fun isUniqueFkDest(fk: ASTForeignKey) =
        !fk.from.foreignKeys.filter { it != fk }.map { it -> it.towards }.contains(fk.towards)
}


