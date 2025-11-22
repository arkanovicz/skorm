package com.republicate.skorm.core

import com.republicate.kson.Json
import com.republicate.skorm.*

fun AttributeHolder.parseAndDefine(name: String, query: String): AttributeDefinition {
    (processor as CoreProcessor).let { coreProcessor ->
        val queryDef = AttributeDefinition.parse(query, schema?.name ?: "", coreProcessor.readMapper)
        if (queryDef !is SimpleQuery) throw SkormException("non-mutation attribute can only contain a single query")
        coreProcessor.define("${path}/$name", queryDef)
        return queryDef
    }
}
inline fun <reified T> AttributeHolder.scalarAttribute(name: String, query: String): ScalarAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return scalarAttribute<T>(name, queryDef.parameters())
}

inline fun <reified T: Json.MutableObject> AttributeHolder.rowAttribute(name: String, query: String, resultEntity: Entity? = null): RowAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return rowAttribute(name, queryDef.parameters(), resultEntity)
}

inline fun <reified T: Json.MutableObject> AttributeHolder.nullableRowAttribute(name: String, query: String, resultEntity: Entity? = null): NullableRowAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return nullableRowAttribute(name, queryDef.parameters(), resultEntity)
}

inline fun <reified T: Json.MutableObject> AttributeHolder.rowSetAttribute(name: String, query: String, resultEntity: Entity? = null): RowSetAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return rowSetAttribute(name,queryDef.parameters(), resultEntity)
}

fun AttributeHolder.mutationAttribute(name: String, query: String): MutationAttribute {
    val coreProcessor = processor as CoreProcessor
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "", coreProcessor.readMapper)
    coreProcessor.define("${path}/$name", queryDef)
    return mutationAttribute(name, queryDef.parameters())
}
