package com.republicate.skorm

internal fun Entity.generateBrowseStatement(): QueryDefinition {
    val stmt = "SELECT ${
        fields.values.joinToString(", ") { it.name }
    } FROM ${schema.name}.$name;"
    return QueryDefinition(stmt, emptyList())
}

internal fun Entity.generateFetchStatement(): QueryDefinition {
    val stmt = "SELECT ${
        fields.values.joinToString(", ") { it.name }
    } FROM ${schema.name}.$name WHERE ${
        primaryKey.joinToString(" AND ") { "${it.name} = ?" }
    };"
    return QueryDefinition(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateInsertStatement(params: Collection<String>): QueryDefinition {
    var count: Int
    val names = params.joinToString(",")
    val values = Array(names.length) { "?" }.joinToString(",")
    val stmt = "INSERT INTO ${schema.name}.{$name} ($names) VALUES ($values);"
    return QueryDefinition(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateDeleteStatement(): QueryDefinition {
    val stmt = "DELETE FROM ${schema.name}.$name WHERE ${
        primaryKey.joinToString(" AND ") { "${it.name} = ?" }
    };"
    return QueryDefinition(stmt, primaryKey.map { it.name })
}

internal fun Entity.generateUpdateStatement(params: Collection<String>): QueryDefinition {
    val stmt = "UPDATE ${schema.name}.$name SET ${
        params.joinToString(", ") { "$it = ?" }
    } WHERE ${
        primaryKey.map { "${it.name} = ?" }.joinToString(" AND ")
    };"
    return QueryDefinition(stmt, primaryKey.map { it.name })
}
