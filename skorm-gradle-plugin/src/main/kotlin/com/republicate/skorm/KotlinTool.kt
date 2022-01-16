package com.republicate.skorm

import com.republicate.kddl.ASTField
import com.republicate.kddl.ASTSchema
import groovyjarjarantlr.SemanticException
import java.util.*
import com.republicate.skorm.GeneratePropertiesCodeTask.PropertyType.*

fun String.lowercase() = toLowerCase(Locale.ROOT)

class KotlinTool {
    companion object {
        private val decomp = Regex("^(\\w+)\\s*(?:\\((?:(\\d+|'(?:[^']|'')*')(?:\\s*,\\s*(\\d+|'(?:[^']|'')*'))*)?\\))?$")
        private val text = setOf("text", "varchar")
    }

    fun type(name: String, type: String): String {
        val match = decomp.matchEntire(type) ?: throw SemanticException("invalid type: $type")
        val base = match.groups[1]!!.value.toLowerCase(Locale.ROOT)
        return when(base) {
            "text", "varchar" -> "String"
            "enum" -> pascal(name) + "Enum"
            "serial" -> "Int"
            "date" -> "LocalDate"
            "datetime" -> "Instant"
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

    fun camel(identifier: String) = snakeToCamel.apply(identifier)

    fun pascal(identifier: String) = snakeToPascal.apply(identifier)

    fun propertyType(property: GeneratePropertiesCodeTask.ObjectProperty): String {
        return when (property.type) {
            SCALAR -> "Any?"
            ROW -> "${pascal(property.entity!!)}${if (property.nullable) "?" else ""}"
            ROWSET -> "Sequence<${property.entity?.let {pascal(it)} ?: "Instance"}>"
        }
    }
}
