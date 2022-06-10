package com.republicate.skorm

import com.republicate.kson.Json
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

fun StringValues.toMap() = entries().associate { it.key to it.value.firstOrNull() }

fun Route.rest(entity: Entity) {
    route(entity.name) {
        val idPath = entity.primaryKey.joinToString("/") { "{${it.name}}" }
        route(idPath) {
            get {
                 entity.fetch(call.parameters.toMap())?.also {
                     call.respond(it)
                 } ?: run {
                     call.response.status(HttpStatusCode.NotFound)
                 }
            }
            put {
                entity.fetch(call.parameters)?.also {
                    it.putAll(call.request.queryParameters.toMap())
                    it.update()
                    call.response.status(HttpStatusCode.OK)
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                }
            }
            delete {
                entity.fetch(call.parameters)?.also {
                    it.delete()
                    call.response.status(HttpStatusCode.OK)
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                }
            }
        }
        get {
            call.respond(entity.browse().toCollection(Json.MutableArray()))
        }
        post {
            val instance = entity.new()
            instance.putAll(call.request.queryParameters.toMap())
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
