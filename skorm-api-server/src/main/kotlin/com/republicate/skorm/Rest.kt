package com.republicate.skorm

import com.republicate.kson.Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.*

private val logger = KotlinLogging.logger("skorm.api")

private fun StringValues.toMap() = entries().associate { it.key to it.value.firstOrNull() }

private suspend fun ApplicationCall.allParameters() = when (request.httpMethod) {
    HttpMethod.Get -> parameters.toMap() + request.queryParameters.toMap()
    else -> parameters.toMap() + receiveParameters().toMap()
}

val Parameters.params get() = toMap()

fun Route.rest(entity: Entity) {
    route(entity.name) {
        logger.info { "Defining routes for entity ${entity.name}"}
        val idPath = entity.primaryKey.joinToString("/") { "{${it.name}}" }
        route(idPath) {
            logger.info { "Defining routes for entity ${entity.name} instances with pk path ${idPath}"}
            get {
                 entity.fetch(call.allParameters())?.also {
                     call.respond(it)
                 } ?: run {
                     call.response.status(HttpStatusCode.NotFound)
                 }
            }
            put {
                entity.fetch(call.allParameters())?.also {
                    it.putRawFields(call.allParameters())
                    it.update()
                    call.response.status(HttpStatusCode.OK)
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                }
            }
            delete {
                entity.fetch(call.allParameters())?.also {
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
                            call.respond(entity.instanceAttributes.eval<String>(attribute.key, call.allParameters()))
                        }
                    }
                    is CharAttribute, is NullableCharAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Char>(attribute.key, call.allParameters()))
                        }
                    }
                    is BooleanAttribute, is NullableBooleanAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Boolean>(attribute.key, call.allParameters()))
                        }
                    }
                    is ByteAttribute, is NullableByteAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Byte>(attribute.key, call.allParameters()))
                        }
                    }
                    is ShortAttribute, is NullableShortAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Short>(attribute.key, call.allParameters()))
                        }
                    }
                    is IntAttribute, is NullableIntAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Int>(attribute.key, call.allParameters()))
                        }
                    }
                    is LongAttribute, is NullableLongAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Long>(attribute.key, call.allParameters()))
                        }
                    }
                    is FloatAttribute, is NullableFloatAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Float>(attribute.key, call.allParameters()))
                        }
                    }
                    is DoubleAttribute, is NullableDoubleAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Double>(attribute.key, call.allParameters()))
                        }
                    }
                    is LocalDateAttribute, is NullableLocalDateAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalDate>(attribute.key, call.allParameters()))
                        }
                    }
                    is LocalDateTimeAttribute, is NullableLocalDateTimeAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalDateTime>(attribute.key, call.allParameters()))
                        }
                    }
                    is LocalTimeAttribute, is NullableLocalTimeAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<LocalTime>(attribute.key, call.allParameters()))
                        }
                    }
                    is BytesAttribute, is NullableBytesAttribute ->  {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.eval<Array<Byte>>(attribute.key, call.allParameters()))
                        }
                    }
                    is RowAttribute, is NullableRowAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.retrieve<Json.Object>(attribute.key, call.allParameters()))
                        }
                    }
                    is InstanceAttribute<*>, is NullableInstanceAttribute<*> -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.retrieve<Instance>(attribute.key, call.allParameters()))
                        }
                    }
                    is RowSetAttribute -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.query<Json.Object>(attribute.key, call.allParameters()))
                        }
                    }
                    is BagAttribute<*> -> {
                        get(attribute.key) {
                            call.respond(entity.instanceAttributes.query<Instance>(attribute.key, call.allParameters()))
                        }
                    }
                    is MutationAttribute -> {
                        post(attribute.key) {
                            logger.info { "@@@ content type = ${call.request.contentType().contentType}" }
                            val params = call.receiveParameters().toMap() + call.parameters.toMap()
                            call.respond(entity.instanceAttributes.perform(attribute.key, params) as Long)
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
            instance.putRawFields(call.allParameters())
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

