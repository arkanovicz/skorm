package com.republicate.skorm

import com.republicate.skorm.GeneratePropertiesCodeTask.PropertyType.*
import com.republicate.skorm.GeneratePropertiesCodeTask.States.*
import org.apache.velocity.VelocityContext
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.FileWriter

abstract class GeneratePropertiesCodeTask : GenerateTask() {

    init {
        description = "Skorm code generation of database properties extension functions"
        group = "code generation"
    }

    override val templatePath = "templates/skorm-properties.vtl"

    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    private val properties: ObjectProperties by lazy {
        parseProperties(propertiesFile.asFile.get() ?: throw RuntimeException("modelProperties file not found"))
    }

    @TaskAction
    fun generatePropertiesCode() {
        if (propertiesFile.isPresent) {
            generateCode()
        } else {
            logger.lifecycle("$tag . No model properties file found, skipping properties code generation.")
        }
    }

    override fun populateContext(context: VelocityContext) {
        context.put("root", properties)
    }


    enum class States {
        INITIAL, AFTER_KEYWORD, BEFORE_TYPE, AFTER_TYPE
    }

    enum class PropertyType {
        SCALAR, ROW, ROWSET
    }

    class ObjectProperty(
        val name: String,
        val type: PropertyType,
        val entity: String?,
        val nullable: Boolean
    ) {
        val queries = mutableListOf<String>()
    }

    class ObjectProperties(val name: String, val parent: ObjectProperties?) {
        val children = mutableListOf<ObjectProperties>()
        val properties = mutableListOf<ObjectProperty>()
    }

    fun parseProperties(file: File): ObjectProperties {
        // for now, very basic parsing while waiting for a true antlr parsing - TODO
        val content = file.readText()
        var state = INITIAL
        val regex = Regex("\\s*(\\w+|[:?{}*]|=\\s*sql\\s*\\{[^}]+\\s*)", RegexOption.DOT_MATCHES_ALL)
        var keyword: String? = null
        var type: String? = null
        var cardinality: String? = null
        var query: String?
        var rootObject: ObjectProperties? = null
        var currentObject: ObjectProperties? = null
        fun reset() { keyword = null; type = null; cardinality = null }
        regex.findAll(content).forEach {
            val token = it.groups[1]!!.value
            val pos = it.groups[1]!!.range.start
            when {
                token == ":" -> {
                    check(state == AFTER_KEYWORD) { "Unexpected ':' at pos $pos" }
                    state = BEFORE_TYPE
                    type = null
                    cardinality = null
                }
                token == "?" || token == "*" -> {
                    check(state == AFTER_TYPE) { "Unexpected cardinality qualifier at pos $pos"}
                     cardinality = token
                }
                token == "{" -> {
                    check(state == AFTER_KEYWORD) { "Unexpected '{' at pos $pos" }
                    currentObject = ObjectProperties(keyword!!, currentObject)
                    if (rootObject == null) rootObject = currentObject
                    else rootObject!!.children.add(currentObject!!)
                    reset()
                }
                token == "}" -> {
                    currentObject = currentObject?.parent
                }
                token.startsWith("=") -> {
                    check(state == AFTER_KEYWORD || state == AFTER_TYPE) { "Unexpected '=' at pos $pos" }
                    query = token.substring(1).trim()
                    val property = ObjectProperty(
                        keyword!!,
                        if (type == null) SCALAR
                        else if (cardinality == "*") ROWSET
                        else ROW,
                        type,
                        cardinality == "?"
                    )
                    currentObject!!.properties.add(property.also { it.queries.add(query!!) })
                    reset()
                }
                else -> { // means a keyword by construct
                    when (state) {
                        INITIAL -> {
                            keyword = token
                            state = AFTER_KEYWORD
                        }
                        BEFORE_TYPE -> {
                            type = token
                            state = AFTER_TYPE
                        }
                        else -> throw RuntimeException("unhandled case")
                    }
                }
            }
        }
        return rootObject ?: throw RuntimeException("no root object found")
    }
}
