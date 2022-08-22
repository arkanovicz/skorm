package com.republicate.skorm

import com.republicate.kson.Json
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger("skorm.client")

fun ContentNegotiation.Config.json() {
    register(
        contentType = ContentType.Application.Json,
        converter = KsonConverter)
}

object KsonConverter: ContentConverter {
    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val buffer = StringBuilder()
        var first = true
        while (true) {
            if (first) first = false
            else buffer.append('\n')
            if (!content.readUTF8LineTo(buffer)) break
        }
        return Json.parseValue(buffer.toString())
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent? {
        if (value !is Json) throw SkormException("content is not Json")
        return TextContent(value.toString(), ContentType.Application.Json)
    }

}

class ApiClient(val baseUrl: String) : Processor {

    override val configTag = "client"
    override val config = Configuration()
    override val restMode = true

    private val client = HttpClient() {
        install(ContentNegotiation) {
            json()
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
        val stringParams = parameters.filterValues { it != null }.mapValues { listOf(it.value?.toString() ?: "") }
        client.request("$baseUrl$url") {
            method = httpMethod
            when (method) {
                HttpMethod.Get -> parametersOf(stringParams)
                else -> setBody(FormDataContent(parametersOf(stringParams)))
            }
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
        logger.info { "eval $path $params" }
        val response = get(path, params)
        return response.body()
    }

    override suspend fun retrieve(path: String, params: Map<String, Any?>, result: Entity?): Json.Object? {
        logger.info { "retrieve $path $params ${result?.let { "as ${result.name}" } ?: ""} with params ${params.entries.joinToString(" ") { "${it.key}=${it.value}" }}" }
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
        logger.info { "@@@ actual path = ${restPath}" }
        val response = get(restPath, restParams)
        val json = response.body<Json.Object>()
        return result?.new()?.also {
            it.putFields(json)
            it.setClean()
        } ?: json
    }

    override suspend fun query(path: String, params: Map<String, Any?>, result: Entity?): Sequence<Json.Object> {
        logger.info { "query $path $params ${result?.let { "as $result.name" } ?: ""}" }
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

        // CB TODO - stream parsing of Sequence<Object> in Kson
        // val sequence = response.body<Sequence<Json.Object>>()
        logger.info {  "Reading response "}
        val all = response.body<Json.Array>()
        logger.info { "Got response $all" }
        val sequence = all.asSequence() as Sequence<Json.Object>

        return result?.let { sequence.map { obj ->
            result.new().also {
                it.putFields(obj)
                it.setClean()
            }
        } }?.asSequence() ?: sequence
    }

    override suspend fun perform(path: String, params: Map<String, Any?>): Long {
        logger.info { "perform $path $params" }
        val response = when(path.split("/").last()) {
            "update" -> put(path, params)
            "delete" -> delete(path, params)
            else -> post(path, params)
        }
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
