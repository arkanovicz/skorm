package com.republicate.skorm

typealias IdentifierMapper = (String) -> String

val identityMapper: IdentifierMapper = { it }

val snakeToCamel:  IdentifierMapper = { snake ->
    if (!snake.contains("_")) snake
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

val camelToSnake: IdentifierMapper = { camel ->
    val parts = camel.split("(?<=[a-z])(?=[A-Z])") // CB TODO factorize regex building
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

fun IdentifierMapper.compose(other: IdentifierMapper): IdentifierMapper {
    return when {
        this === identityMapper -> other
        other === identityMapper -> this
        else -> { it -> this(other(it)) }
    }
}