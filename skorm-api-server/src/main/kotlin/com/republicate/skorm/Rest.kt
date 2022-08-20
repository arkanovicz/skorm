package com.republicate.skorm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.republicate.kson.Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val logger = KotlinLogging.logger("skorm.api")

private fun StringValues.toMap() = entries().associate { it.key to it.value.firstOrNull() }

private val ApplicationCall.params get() = parameters.toMap()

val Parameters.params get() = toMap()

fun Route.rest(entity: Entity) {
    route(entity.name) {
        logger.info { "Defining routes for entity ${entity.name}"}
        val idPath = entity.primaryKey.joinToString("/") { "{${it.name}}" }
        route(idPath) {
            logger.info { "Defining routes for entity ${entity.name} instances with pk path ${idPath}"}
            get {
                 entity.fetch(call.params)?.also {
                     call.respond(it)
                 } ?: run {
                     call.response.status(HttpStatusCode.NotFound)
                 }
            }
            put {
                entity.fetch(call.params)?.also {
                    it.putAll(call.params)
                    it.update()
                    call.response.status(HttpStatusCode.OK)
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                }
            }
            delete {
                entity.fetch(call.params)?.also {
                    it.delete()
                    call.response.status(HttpStatusCode.OK)
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                }
            }
            logger.info { "Defining attributes for entity ${entity.name}"}
            for (attribute in entity.instanceAttributes.attributes) {
                logger.info { "Defining attribute ${entity.path}/${attribute.value.name}"}
                when (attribute.value) {
                    is StringAttribute, is NullableStringAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<String>(attribute.key, call.params))
                        }
                    }
                    is CharAttribute, is NullableCharAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Char>(attribute.key, call.params))
                        }
                    }
                    is BooleanAttribute, is NullableBooleanAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Boolean>(attribute.key, call.params))
                        }
                    }
                    is ByteAttribute, is NullableByteAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Byte>(attribute.key, call.params))
                        }
                    }
                    is ShortAttribute, is NullableShortAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Short>(attribute.key, call.params))
                        }
                    }
                    is IntAttribute, is NullableIntAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Int>(attribute.key, call.params))
                        }
                    }
                    is LongAttribute, is NullableLongAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Long>(attribute.key, call.params))
                        }
                    }
                    is BigIntegerAttribute, is NullableBigIntegerAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<BigInteger>(attribute.key, call.params))
                        }
                    }
                    is FloatAttribute, is NullableFloatAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Float>(attribute.key, call.params))
                        }
                    }
                    is DoubleAttribute, is NullableDoubleAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Double>(attribute.key, call.params))
                        }
                    }
                    is BigDecimalAttribute, is NullableBigDecimalAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<BigDecimal>(attribute.key, call.params))
                        }
                    }
                    is InstantAttribute, is NullableInstantAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Instant>(attribute.key, call.params))
                        }
                    }
                    is LocalDateAttribute, is NullableLocalDateAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalDate>(attribute.key, call.params))
                        }
                    }
                    is LocalDateTimeAttribute, is NullableLocalDateTimeAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalDateTime>(attribute.key, call.params))
                        }
                    }
                    is LocalTimeAttribute, is NullableLocalTimeAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalTime>(attribute.key, call.params))
                        }
                    }
                    is BytesAttribute, is NullableBytesAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Array<Byte>>(attribute.key, call.params))
                        }
                    }
                    is RowAttribute, is NullableRowAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.retrieve<Json.Object>(attribute.key, call.params))
                        }
                    }
                    is InstanceAttribute<*>, is NullableInstanceAttribute<*> -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.retrieve<Instance>(attribute.key, call.params))
                        }
                    }
                    is RowSetAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.query<Json.Object>(attribute.key, call.params))
                        }
                    }
                    is BagAttribute<*> -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.query<Instance>(attribute.key, call.params))
                        }
                    }
                    is MutationAttribute -> {
                        post(attribute.key) {
                            call.respond(entity.instanceAttributes.perform(attribute.key, call.params) as Long)
                        }
                    }
                    is TransactionAttribute -> TODO()
                }
            }
        }
        get {
            call.respond(entity.browse().toCollection(Json.MutableArray()))
        }
        post {
            val instance = entity.new()
            // instance.putAll(call.request.queryParameters.toMap())
            instance.putAll(call.params)
            instance.insert()
            call.response.status(HttpStatusCode.OK)
        }
    }
}

fun Route.rest(schema: Schema) {
    route(schema.name) {
        for (entity in schema.entities) {
            rest(entity)
        }
    }
}

