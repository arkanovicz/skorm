package com.republicate.skorm

internal fun Entity.generateBrowseStatement(): Query {
    val stmt = "SELECT ${
        fields.values.joinToString(", ") { it.name }
    } FROM ${schema.name}.$name;"
    return Query(stmt, emptyList())
}

internal fun Entity.generateFetchStatement(): Query {
    val stmt = "SELECT ${
        fields.values.joinToString(", ") { it.name }
    } FROM ${schema.name}.$name WHERE ${
        primaryKey.joinToString(" AND ") { "${it.name} = ?" }
    };"
    return Query(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateInsertStatement(): Query {
    var count: Int
    val names = fields.values
        .filter { !it.generated }
        .also { count = it.size }
        .joinToString(",") { it.name }
    val values = Array(count) { "?" }.joinToString(",")
    val stmt = "INSERT INTO ${schema.name}.{$name} ($names) VALUES ($values);"
    return Query(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateDeleteStatement(): Query {
    val stmt = "DELETE FROM ${schema.name}.$name WHERE ${
        primaryKey.joinToString(" AND ") { "${it.name} = ?" }
    };"
    return Query(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateUpdateStatement(params: Collection<String>): Query {
    val stmt = "UPDATE ${schema.name}.$name SET ${
        params.joinToString(", ") { "$it = ?" }
    } WHERE ${
        primaryKey.map { "${it.name} = ?" }.joinToString(" AND ")
    };"
    return Query(stmt, primaryKey.map { it.name })
}
