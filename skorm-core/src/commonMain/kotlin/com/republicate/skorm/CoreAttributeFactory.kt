package com.republicate.skorm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

inline fun <reified T> AttributeHolder.scalarAttribute(name: String, query: String): ScalarAttribute<T> {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("scalar attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return scalarAttribute<T>(name, queryDef.parameters())
}

fun AttributeHolder.rowAttribute(name: String, query: String): RowAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("row attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return rowAttribute(name, queryDef.parameters())
}

fun AttributeHolder.nullableRowAttribute(name: String, query: String): NullableRowAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("row attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return nullableRowAttribute(name, queryDef.parameters())
}

fun AttributeHolder.instanceAttribute(name: String, resultEntity: Entity, query: String): InstanceAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("instance attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return instanceAttribute(name, resultEntity, queryDef.parameters())
}

fun AttributeHolder.nullableInstanceAttribute(name: String, resultEntity: Entity, query: String): NullableInstanceAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("instance attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return nullableInstanceAttribute(name, resultEntity, queryDef.parameters())
}
fun AttributeHolder.rowSetAttribute(name: String, query: String): RowSetAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("row set attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return rowSetAttribute(name,queryDef.parameters())
}

fun AttributeHolder.bagAttribute(name: String, resultEntity: Entity, query: String): BagAttribute {
    val queryDef = Query.parse(query)
    if (queryDef !is SimpleQuery) throw SkormException("bag attribute can only contain a single query")
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return bagAttribute(name, resultEntity, queryDef.parameters())
}
fun AttributeHolder.mutationAttribute(name: String, query: String): MutationAttribute {
    val queryDef = Query.parse(query)
    (processor as CoreProcessor).define("${path}/$name", queryDef)
    return mutationAttribute(name,queryDef.parameters())
}
