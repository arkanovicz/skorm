package com.republicate.skorm

import com.republicate.kson.Json
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger("skorm.client")


/* CB TODO - deprecated in ktor-2.x ; but documentation is still not ready */
class KsonSerializer(): JsonSerializer {
    override fun write(data: Any, contentType: ContentType) = TextContent(data.toString(), contentType)
    override fun read(type: TypeInfo, body: Input): Any {
        val str = body.readText()
        logger.info { "received: <$str>"}
        return Json.parseValue(str) ?: "null"
    }
}

class ApiClient(val baseUrl: String) : Processor {

    override val config = Configuration()

    private val client = HttpClient() {
        install(JsonPlugin) {
            serializer = KsonSerializer()
        }
        defaultRequest {
            url(baseUrl)
            headers.appendMissing(HttpHeaders.ContentType, listOf(ContentType.Application.Json.toString()))
        }
    }

    override fun initialize() {

    }

    private suspend fun process(url: String, parameters: Map<String, Any?>, httpMethod: HttpMethod): HttpResponse = coroutineScope {
        logger.info { "$httpMethod $url" }
        client.request("$baseUrl$url") {
            method = httpMethod
            parametersOf(parameters.filterValues { it != null }.mapValues { listOf(it.value?.toString() ?: "") })
        }
    }

    private suspend fun get(url: String, parameters: Map<String, Any?> = mapOf()) = process(url, parameters, HttpMethod.Get)
    private suspend fun post(url: String, parameters: Map<String, Any?> = mapOf()) = process(url, parameters, HttpMethod.Post)
    private suspend fun put(url: String, parameters: Map<String, Any?> = mapOf()) = process(url, parameters, HttpMethod.Put)
    private suspend fun delete(url: String, parameters: Map<String, Any?> = mapOf()) = process(url, parameters, HttpMethod.Delete)

//    override suspend fun insert(instance: Instance): GeneratedKey? {
//        val response = post("${instance.entity.schema.database.name}/${instance.entity.schema.name}/${instance.entity.name}", instance)
//        return response.body<Long>()
//    }
//
//    override suspend fun update(instance: Instance) {
//        val pkString = instance.entity.primaryKey.joinToString("/") { instance.getString(it.sqlName)!! }
//        put("${instance.entity.schema.database.name}/${instance.entity.schema.name}/${instance.entity.name}/$pkString", instance)
//    }
//
//    override suspend fun delete(instance: Instance) {
//        val pkString = instance.entity.primaryKey.joinToString("/") { instance.getString(it.sqlName)!! }
//        delete("${instance.entity.schema.database.name}/${instance.entity.schema.name}/${instance.entity.name}/$pkString", instance)
//    }

    override suspend fun eval(path: String, params: Map<String, Any?>): Any? {
        val response = get(path, params)
        return response.body()
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Json.Object? {
        var restPath = path
        var restParams = params
        if (result != null) {
            val name = path.split("/").last()
            when (name) {
                "fetch" -> {
                    restPath = restPath.substring(0, restPath.length - name.length) + params.entries.joinToString("/") { it.value.toString() }
                    restParams = emptyMap()
                }
            }
        }
        val response = get(restPath, restParams)
        val json = response.body<Json.Object>()
        return result?.new()?.also {
            it.putAll(json)
            it.setClean()
        } ?: json
    }

    override suspend fun query(path: String, params: Map<String, Any?>, result: Entity?): Sequence<Json.Object> {
        var restPath = path
        var restParams = params
        if (result != null) {
            val name = path.split("/").last()
            when (name) {
                "browse" -> {
                    restPath = restPath.substring(0, restPath.length - name.length)
                    restParams = emptyMap()
                }
            }
        }
        val response = get(restPath, restParams)
        val sequence = response.body<Sequence<Json.Object>>()
        return result?.let { sequence.map { obj ->
            result.new().also {
                it.putAll(obj)
                it.setClean()
            }
        } }?.asSequence() ?: sequence
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        val response = post(path, params)
        return response.body()
    }

    override suspend fun begin(): Transaction {
        TODO("Not yet implemented")
    }

//    override suspend fun savePoint(name: String) {
//        TODO("Not yet implemented")
//    }
//
//    override suspend fun rollback(savePoint: String?) {
//        TODO("Not yet implemented")
//    }
//
//    override suspend fun commit() {
//        TODO("Not yet implemented")
//    }
}
