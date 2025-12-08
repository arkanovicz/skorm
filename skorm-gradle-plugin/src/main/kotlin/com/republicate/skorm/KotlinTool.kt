package com.republicate.skorm

import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import groovyjarjarantlr.SemanticException
import org.atteo.evo.inflector.English
import java.util.*

@Suppress("unused")
class KotlinTool {
    companion object {
        private val decomp =
            Regex("^(\\w+)\\s*(?:\\((?:(\\d+|'(?:[^']|'')*')(?:\\s*,\\s*(\\d+|'(?:[^']|'')*'))*)?\\))?$")
        private val text = setOf("text", "varchar")
        private val enInflector = English(English.MODE.ENGLISH_CLASSICAL)
    }

    fun type(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.lowercase(Locale.ROOT)
        return when (base) {
            "boolean" -> "Boolean"
            "text", "varchar", "clob" -> "String" // TODO streams for "text" and "clob"
            "enum" -> pascal(name)
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
            else -> throw SemanticException("unsupported type: $type")
        }
    }

    fun getter(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.lowercase(Locale.ROOT)
        return when (base) {
            "blob" -> "getBytes"
            else -> "get${type(name, base)}"
        }
    }

    fun enums(schema: ASTSchema): List<ASTField> {
        return schema.tables.values.flatMap {
            it.fields.values
        }.filter {
            it.type.startsWith("enum")
        }
    }

    fun enumValues(field: ASTField): List<String> {
        return field.type.substringAfter('(').substringBeforeLast(')').split(',').map { it.removeSurrounding("'") }
    }

    fun isEnum(type: String) = type.startsWith("enum")

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
                "${fk.from.name}.${it.second.name} = {${it.first.name}}"
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


