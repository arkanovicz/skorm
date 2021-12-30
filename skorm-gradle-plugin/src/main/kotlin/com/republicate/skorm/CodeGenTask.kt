package com.republicate.skorm

import com.republicate.kddl.Database
import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.LogTool
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.FileWriter
import java.util.*

abstract class CodeGenTask : DefaultTask() {

    init {
        description = "SKORM code generation plugin"
        group = "code generation"
    }

    override fun dependsOn(vararg paths: Any?): Task? {
        return super.dependsOn(*paths)
    }

    @get:InputFile
    @get:Option(option = "source", description = "Source kddl model")
    @get:Optional
    abstract val source: RegularFileProperty

    @get:Input
    @get:Option(option = "datasource", description = "Datasource JDBC URL with credentials")
    @get:Optional
    abstract val datasource: Property<String>

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
        logger.lifecycle("$tag datasource is: ${source.orNull}")
        logger.lifecycle("$tag destPackage is: ${destPackage.orNull}")
        logger.lifecycle("$tag destFile is: ${destFile.orNull}")

        val foundSource = source.orNull
        val foundDatasource = datasource.orNull

        if ((foundSource == null) == (foundDatasource == null)) {
            throw RuntimeException("$tag expecting skorm.source or skorm.datasource")
        }

        val database = when {
            foundSource != null -> {
                val file = project.file(foundSource) ?: throw RuntimeException("source file not found")
                val ddl = Utils.getFile(file.absolutePath)
                parse(ddl)
            }
            foundDatasource != null -> {
                reverse(foundDatasource)
            }
            else -> throw RuntimeException("this cannot happen")
        }

        val engine = VelocityEngine()
        val prop = Properties()
        prop.put("resource.loaders", "class")
        prop.put("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
        prop.put("velocimacro.library.path", "templates/macros.vtl")
        prop.put("runtime.log.track_location", "true")
        engine.init(prop)
        val context = VelocityContext()
        context.put("kotlin", KotlinTool())
        context.put("log", LogTool())
        context.put("package", destPackage.get())
        context.put("database", database)
        val writer = FileWriter(destFile.get().asFile)
        val template = engine.getTemplate("templates/skorm-sources.vtl") ?: throw RuntimeException("template not found")
        template.merge(context, writer)
        writer.close()
    }
}
