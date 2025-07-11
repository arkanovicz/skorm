package com.republicate.skorm

import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.ASTTable
import groovyjarjarantlr.SemanticException
import org.atteo.evo.inflector.English
import java.util.*

fun String.lowercase() = toLowerCase(Locale.ROOT)

class KotlinTool {
    companion object {
        private val decomp =
            Regex("^(\\w+)\\s*(?:\\((?:(\\d+|'(?:[^']|'')*')(?:\\s*,\\s*(\\d+|'(?:[^']|'')*'))*)?\\))?$")
        private val text = setOf("text", "varchar")
        private val enInflector = English(English.MODE.ENGLISH_CLASSICAL)
    }

    fun type(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.toLowerCase(Locale.ROOT)
        return when (base) {
            "boolean" -> "Boolean"
            "text", "varchar", "clob" -> "String" // TODO streams for "text" and "clob"
            "enum" -> pascal(name) + "Enum"
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
            "money", "numeric", "decimal" -> "BigDecimal"
            "blob" -> "ByteArray"
            "uuid" -> "Uuid"
            "binary", "varbinary" -> "ByteArray" // TODO streams
            "json" -> "Json"
            else -> throw SemanticException("unsupported type: $type")
        }
    }

    fun getter(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.toLowerCase(Locale.ROOT)
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

    fun plural(str: String) = enInflector.getPlural(str)

    fun foreignKeyForwardQuery(fk: ASTForeignKey): String {
        return "SELECT * FROM ${fk.towards.schema.name}.${fk.towards.name} WHERE ${
            fk.towards.getPrimaryKey().zip(fk.fields).joinToString(" AND ") {
                "${fk.towards.name}.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun foreignKeyReverseQuery(fk: ASTForeignKey): String {
        return "SELECT * FROM ${fk.from.schema.name}.${fk.from.name} WHERE ${
            fk.fields.zip(fk.towards.getPrimaryKey()).joinToString(" AND ") {
                "${fk.from.name}.${it.second.name} = {${it.first.name}}"
            }
        };"
    }

    fun joinTableQuery(join: ASTTable, reverse: Boolean = false): String {
        val fromFk = if (reverse) join.foreignKeys.last() else join.foreignKeys.first()
        val towardsFk = if (reverse) join.foreignKeys.first() else join.foreignKeys.last()
        val from = fromFk.towards
        val towards = towardsFk.towards
        val join = fromFk.from
        assert(join == towardsFk.from)
        return "SELECT towards_table.* FROM ${join.schema.name}.${join.name} AS join_table JOIN ${towards.schema.name}.${towards.name} AS towards_table ON ${
            towards.getPrimaryKey().zip(towardsFk.fields).joinToString(" AND ") {
                "towards_table.${it.first.name} = join_table.${it.second.name}"
            }
        } WHERE ${
            fromFk.fields.zip(from.getPrimaryKey()).joinToString(" AND ") {
                "join_table.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun fieldNames(fields: Set<ASTField>) = fields.joinToString(",") { "\"${it.name}\"" }

    fun names(fields: Set<String>) = fields.joinToString(",") { "\"$it\"" }

    // deprecated
    // fun arguments(fields: Set<String>) = fields.joinToString(",") { "${it}: Any?" }

    fun typedArguments(fields: Set<Pair<String, String?>>) = fields.map {
        "${it.first}: ${it.second ?: "Any?"}"
    }.joinToString(" ,")

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


