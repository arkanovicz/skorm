package com.republicate.skorm

fun Entity.register() {
    schema.processor.register(this)
}

fun Attribute<*>.register(statement: String) {
    val queries = Query.parse(statement)
    val def = when (queries.size) {
        0 -> throw SkormException("could not parse statement")
        1 -> SimpleQuery(queries.first())
        else -> MultipleQuery(queries)
    }
    (holder.processor as CoreProcessor).define("${holder.path}/$name", def)
}
