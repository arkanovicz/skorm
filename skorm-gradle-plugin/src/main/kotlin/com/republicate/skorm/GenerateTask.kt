package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.LogTool
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.FileWriter
import java.util.*

abstract class GenerateTask: DefaultTask() {

    override fun dependsOn(vararg paths: Any?): Task? {
        return super.dependsOn(*paths)
    }

    @get:InputFile
    @get:Option(option = "modelStructure", description = "Source kddl model structure")
    @get:Optional
    abstract val model: RegularFileProperty

    @get:Input
    @get:Option(option = "datasource", description = "Datasource JDBC URL with credentials")
    @get:Optional
    abstract val datasource: Property<String>

    @get:Input
    @get:Option(option = "destPackage", description = "Destination package")
    @get:Optional
    abstract val destPackage: Property<String>

    @get:Option(option = "destFile", description = "Destination file")
    @get:OutputFile
    abstract val destFile: RegularFileProperty

    @Internal
    open val tag = "[skorm]"

    private val engine = VelocityEngine().apply {
        val prop = Properties().apply {
            put("resource.loaders", "class")
            put("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
            put("velocimacro.library.path", "templates/macros.vtl")
            put("runtime.log.track_location", "true")
        }
        init(prop)
    }

    @get:Internal
    protected val database: ASTDatabase by lazy {
        val foundStructure = model.orNull
        val foundDatasource = datasource.orNull
        if ((foundStructure == null) == (foundDatasource == null)) {
            throw RuntimeException("$tag expecting exactly one of skorm.structure or skorm.datasource parameter")
        }
        val db = when {
            foundStructure != null -> {
                val file = project.file(foundStructure) ?: throw RuntimeException("model structure file not found")
                val ddl = Utils.getFile(file.absolutePath)
                parse(ddl)
            }
            foundDatasource != null -> {
                reverse(foundDatasource)
            }
            else -> throw RuntimeException("this cannot happen")
        }
        db
    }

    private val context: VelocityContext by lazy {
        VelocityContext().apply {
            put("kotlin", KotlinTool())
            put("log", logger)
            put("package", destPackage.get())
            put("database", database)
            populateContext(this)
        }
    }

    protected open fun populateContext(context: VelocityContext) {}

    protected fun generateCode(templatePath: String, destFile: RegularFileProperty) {
        logger.lifecycle("$tag modelStructure is: ${model.orNull}")
        logger.lifecycle("$tag datasource is: ${datasource.orNull}")
        logger.lifecycle("$tag destPackage is: ${destPackage.orNull}")
        logger.lifecycle("$tag destFile is: ${destFile.orNull}")

        destFile.get().asFile.parentFile.mkdirs()
        val writer = FileWriter(destFile.get().asFile)

        val template = engine.getTemplate(templatePath) ?: throw RuntimeException("template not found")
        template.merge(context, writer)
        writer.close()
    }
}
