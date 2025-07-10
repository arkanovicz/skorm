package com.republicate.skorm

typealias IdentifierMapper = (String) -> String

val identityMapper: IdentifierMapper = { it }

fun IdentifierMapper.compose(other: IdentifierMapper): IdentifierMapper {
    return when {
        this === identityMapper -> other
        other === identityMapper -> this
        else -> { it -> this(other(it)) }
    }
}

class IdentifiersMapping private constructor (): Transformers<String, String>(stockMappings) {
    companion object {

        val lowercase: IdentifierMapper = { it.toLowerCase() }

        val uppercase: IdentifierMapper = { it.toUpperCase() }

        val snakeToCamel: IdentifierMapper = { snake ->
            if (!snake.contains("_")) snake.lowercase()
            else {
                val parts = snake.lowercase().split("_") // CB TODO factorize regex building
                val builder = StringBuilder()
                var first = true
                for (part in parts) {
                    if (part.length > 0) {
                        builder.append(if (first) part.decapitalize() else part.capitalize())
                        first = false
                    }
                }
                if (builder.length == 0) "_" else builder.toString()
            }
        }

        private val camelBoundary = Regex("(?<=[a-z])(?=[A-Z])")

        val camelToSnake: IdentifierMapper = { camel ->
            val parts = camel.split(camelBoundary) // CB TODO factorize regex building
            val builder = StringBuilder()
            var first = true
            for (part in parts) {
                if (part.length > 0) {
                    if (!first) builder.append('_')
                    builder.append(part.lowercase())
                    first = false
                }
            }
            builder.toString()
        }

        val snakeToPascal: IdentifierMapper = { snake ->
            if (!snake.contains("_")) snake.capitalize()
            else {
                val parts = snake.lowercase().split("_") // CB TODO factorize regex building
                val builder = StringBuilder()
                for (part in parts) {
                    if (part.length > 0) {
                        builder.append(part.capitalize())
                    }
                }
                if (builder.length == 0) "_" else builder.toString()
            }
        }

        val pascalToSnake = camelToSnake

        private val stockMappings = mapOf(
            "lowercase" to lowercase,
            "uppercase" to uppercase,
            "snake_to_camel" to snakeToCamel,
            "camel_to_snake" to camelToSnake,
            "snake_to_pascal" to snakeToPascal,
            "pascal_to_snake" to pascalToSnake,
        )

        private val instance = IdentifiersMapping()

        operator fun get(name: String) = instance[name]
        operator fun set(name: String, op: (String)->String) {
            instance[name] = op
        }
    }
}
