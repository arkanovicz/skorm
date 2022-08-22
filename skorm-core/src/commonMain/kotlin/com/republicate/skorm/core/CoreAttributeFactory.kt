package com.republicate.skorm.core

import com.republicate.skorm.*

inline fun <reified T> AttributeHolder.scalarAttribute(name: String, query: String): ScalarAttribute<T> {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("scalar attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return scalarAttribute<T>(name, queryDef.parameters())
}

fun AttributeHolder.rowAttribute(name: String, query: String): RowAttribute {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("row attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return rowAttribute(name, queryDef.parameters())
}

fun AttributeHolder.nullableRowAttribute(name: String, query: String): NullableRowAttribute {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("row attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return nullableRowAttribute(name, queryDef.parameters())
}

fun <T: Instance>AttributeHolder.instanceAttribute(name: String, query: String, resultEntity: Entity) =
    instanceAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.instanceAttribute(name: String, query: String, factory: InstanceFactory): InstanceAttribute<T> {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("instance attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return instanceAttribute<T>(name, queryDef.parameters(), factory)
}

fun <T: Instance>AttributeHolder.nullableInstanceAttribute(name: String, query: String, resultEntity: Entity) =
    nullableInstanceAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.nullableInstanceAttribute(name: String, query: String, factory: InstanceFactory): NullableInstanceAttribute<T> {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("instance attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return nullableInstanceAttribute<T>(name, queryDef.parameters(), factory)
}

fun AttributeHolder.rowSetAttribute(name: String, query: String): RowSetAttribute {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("row set attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return rowSetAttribute(name,queryDef.parameters())
}

fun <T: Instance>AttributeHolder.bagAttribute(name: String, query: String, resultEntity: Entity) =
    bagAttribute<T>(name, query, resultEntity::new)

fun <T: Instance>AttributeHolder.bagAttribute(name: String, query: String, factory: InstanceFactory): BagAttribute<T> {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    if (queryDef !is SimpleQuery) throw SkormException("bag attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return bagAttribute<T>(name, queryDef.parameters(), factory)
}

fun AttributeHolder.mutationAttribute(name: String, query: String): MutationAttribute {
    val queryDef = AttributeDefinition.parse(query, schema?.name ?: "")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return mutationAttribute(name,queryDef.parameters())
}
