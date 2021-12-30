package com.republicate.skorm

fun interface Filter<T>  {
    companion object {
        fun <U> identity() = Filter<U> { it }
    }
    fun apply(s: T): T
    fun compose(other: Filter<T>): Filter<T>? {
        return Filter<T> { apply(other.apply(it)) }
    }
}

typealias IdentifierFilter = Filter<String>

val snakeToCamel = IdentifierFilter { snake ->
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

val camelToSnake = IdentifierFilter { camel ->
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

val snakeToPascal = IdentifierFilter { snake ->
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