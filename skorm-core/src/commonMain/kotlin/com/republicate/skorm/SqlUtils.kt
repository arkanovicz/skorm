package com.republicate.skorm

internal fun Entity.generateInsertStatement(): String {
    var count: Int
    val names = fields.values
        .filter { !it.generated }
        .also { count = it.size }
        .map { it as ConcreteField }
        .joinToString(",") { it.sqlName }
    val values = Array<String>(count, { "?" }).joinToString(",")
    return "INSERT INTO ${schema.name}.{$name} ($names) VALUES ($values)"
}
