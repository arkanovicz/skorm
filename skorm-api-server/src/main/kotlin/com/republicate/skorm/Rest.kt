package com.republicate.skorm

import com.republicate.kson.Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import mu.KotlinLogging

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
                if (attribute.value is MutationAttribute) {
                    post(attribute.key) {
                        call.response.
                        call.respond(attribute.value.execute(call.params) as Long)
                    }
                } else {
                    get(attribute.key) {
                        val ret = attribute.value.execute(call.params)
                        if (ret == null) call.respond(HttpStatusCode.NotFound) // CB TODO - review
                        else call.respond(ret)
                    }
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

