package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.FileWriter
import java.util.*

abstract class BaseGenerationTask : DefaultTask() {

    override fun dependsOn(vararg paths: Any?): Task {
        return super.dependsOn(*paths)
    }

    @get:Input
    @get:Option(option = "destPackage", description = "Destination package")
    @get:Optional
    abstract val destPackage: Property<String>

    @get:Option(option = "destFile", description = "Destination file")
    @get:OutputFile
    abstract val destFile: RegularFileProperty

    @Internal
    open val tag = "[skorm]"

    private fun getVelocityEngine() = VelocityEngine().apply {
        val prop = Properties().apply {
            put("resource.loaders", "class")
            put("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
            put("velocimacro.library.path", "templates/macros.vtl")
            put("runtime.log.track_location", "true")
        }
        init(prop)
    }

    private fun getVelocityContext() = VelocityContext().apply {
        put("kotlin", KotlinTool())
        put("log", logger)
        put("package", destPackage.get())
        populateContext(this)
    }

    protected open fun populateContext(context: VelocityContext) {}

    protected open fun generateCode(templatePath: String, destFile: RegularFileProperty) {
        logger.lifecycle("$tag templatePath is $templatePath")
        logger.lifecycle("$tag destPackage is: ${destPackage.orNull}")
        logger.lifecycle("$tag destFile is: ${destFile.orNull}")

        destFile.get().asFile.parentFile.mkdirs()
        val writer = FileWriter(destFile.get().asFile)

        val template = getVelocityEngine().getTemplate(templatePath) ?: throw RuntimeException("template not found")
        template.merge(getVelocityContext(), writer)
        writer.close()
    }
}
