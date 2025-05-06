package com.republicate.skorm.core

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

fun AttributeHolder.rowAttribute(name: String, query: String): RowAttribute {
    val queryDef = parseAndDefine(name, query)
    return rowAttribute(name, queryDef.parameters())
}

fun AttributeHolder.nullableRowAttribute(name: String, query: String): NullableRowAttribute {
    val queryDef = parseAndDefine(name, query)
    return nullableRowAttribute(name, queryDef.parameters())
}

fun <T: Instance>AttributeHolder.instanceAttribute(name: String, query: String, resultEntity: Entity) =
    instanceAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.instanceAttribute(name: String, query: String, factory: InstanceFactory): InstanceAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return instanceAttribute<T>(name, queryDef.parameters(), factory)
}

fun <T: Instance>AttributeHolder.nullableInstanceAttribute(name: String, query: String, resultEntity: Entity) =
    nullableInstanceAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.nullableInstanceAttribute(name: String, query: String, factory: InstanceFactory): NullableInstanceAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return nullableInstanceAttribute<T>(name, queryDef.parameters(), factory)
}

fun AttributeHolder.rowSetAttribute(name: String, query: String): RowSetAttribute {
    val queryDef = parseAndDefine(name, query)
    return rowSetAttribute(name,queryDef.parameters())
}

fun <T: Instance>AttributeHolder.bagAttribute(name: String, query: String, resultEntity: Entity) =
    bagAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.bagAttribute(name: String, query: String, factory: InstanceFactory): BagAttribute<T> {
    val queryDef = parseAndDefine(name, query)
    return bagAttribute<T>(name, queryDef.parameters(), factory)
}

fun AttributeHolder.mutationAttribute(name: String, query: String): MutationAttribute {
    val coreProcessor = processor as CoreProcessor
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "", coreProcessor.readMapper)
    coreProcessor.define("${path}/$name", queryDef)
    return mutationAttribute(name, queryDef.parameters())
}
