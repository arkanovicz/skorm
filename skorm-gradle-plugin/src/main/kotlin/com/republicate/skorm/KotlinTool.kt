package com.republicate.skorm

import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTForeignKey
import com.republicate.kddl.ASTSchema
import groovyjarjarantlr.SemanticException
import org.atteo.evo.inflector.English
import java.util.*

fun String.lowercase() = toLowerCase(Locale.ROOT)

class KotlinTool {
    companion object {
        private val decomp = Regex("^(\\w+)\\s*(?:\\((?:(\\d+|'(?:[^']|'')*')(?:\\s*,\\s*(\\d+|'(?:[^']|'')*'))*)?\\))?$")
        private val text = setOf("text", "varchar")
        private val enInflector = English(English.MODE.ENGLISH_CLASSICAL)
    }

    fun type(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.toLowerCase(Locale.ROOT)
        return when(base) {
            "text", "varchar" -> "String"
            "enum" -> pascal(name) + "Enum"
            "serial" -> "Int"
            "date" -> "LocalDate"
            "datetime" -> "LocalDateTime"
            "int", "integer" -> "Int"
            "long" -> "Long"
            "float" -> "Float"
            "double" -> "Double"
            else -> throw SemanticException("unknown type: $type")
        }
    }

    fun enums(schema: ASTSchema): List<ASTField> {
        return schema.tables.values.flatMap {
            it.fields.values
        }.filter {
            it.type.startsWith("enum")
        }
    }

    fun camel(identifier: String) = snakeToCamel(identifier)

    fun pascal(identifier: String) = snakeToPascal(identifier)

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
            fk.fields.zip(fk.towards.getPrimaryKey()).joinToString(" AND ") { 
                "${fk.towards.name}.${it.second.name} = {${it.first.name}}"
            }
        };"
    }

    fun foreignKeyReverseQuery(fk: ASTForeignKey): String {
        return "SELECT * FROM ${fk.from.schema.name}.${fk.from.name} WHERE ${
            fk.fields.zip(fk.towards.getPrimaryKey()).joinToString(" AND ") {
                "${fk.from.name}.${it.first.name} = {${it.second.name}}"
            }
        };"
    }

    fun fieldNames(fields: Set<ASTField>) = fields.joinToString(",") { "\"${it.name}\"" }

    fun names(fields: Set<String>) = fields.joinToString(",") { "\"$it\"" }

    fun arguments(fields: Set<String>) = fields.joinToString(",") { "${it}: Any?" }

    fun values(fields: Set<String>) = fields.joinToString(",")

    fun capitalize(str: String) = str.replaceFirstChar { it.uppercase() }

    fun decapitalize(str: String) = str.replaceFirstChar { it.lowercase() }
}
