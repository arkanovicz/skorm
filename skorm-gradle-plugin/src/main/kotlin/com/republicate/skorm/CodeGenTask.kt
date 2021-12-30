package com.republicate.skorm

import com.republicate.kddl.*
import com.republicate.kddl.Utils.getFile
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.FileWriter
import java.io.StringWriter
import java.util.*

abstract class CodeGenTask : DefaultTask() {

    init {
        description = "SKORM code generation plugin"
        group = "code generation"
    }

    @get:Input
    @get:Option(option = "source", description = "Source: kddl relative path or jdbc URL")
    abstract val source: Property<String>

    @get:Input
    @get:Option(option = "destPackage", description = "Destination package")
    @get:Optional
    abstract val destPackage: Property<String>

    @get:OutputFile
    abstract val destFile: RegularFileProperty

    @TaskAction
    fun generateCode() {
        val tag = "[skorm]"

        logger.lifecycle("$tag source is: ${source.orNull}")
        logger.lifecycle("$tag destPackage is: ${destPackage.orNull}")
        logger.lifecycle("$tag destFile is: ${destFile.orNull}")

        val database = processSource(source.get())

        val engine = VelocityEngine()
        val prop = Properties()
        prop.put("resource.loaders", "class")
        prop.put("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
        prop.put("velocimacro.library.path", "templates/macros.vtl")
        prop.put("runtime.log.track_location", "true")
        engine.init(prop)
        val context = VelocityContext()
        context.put("kotlin", KotlinTool())
        context.put("package", destPackage.get())
        context.put("database", database)
        val writer = FileWriter(destFile.get().asFile)
        val template = engine.getTemplate("templates/skorm-sources.vtl") ?: throw RuntimeException("template not found")
        template.merge(context, writer)
        writer.close()
    }

    private fun processSource(input: String): Database {
        println("@@@@@@@ dependencies: ${project.buildscript.dependencies}")
        println("@@@@@@@ configurations: ${project.buildscript.configurations}")
        return when {
            input.startsWith("jdbc:") -> {
                reverse(input)
            }
            else -> {
                val file = project.file(input) ?: throw RuntimeException("source file not found")
                val ddl = Utils.getFile(file.absolutePath)
                parse(ddl)
            }
        }
    }
}
