package com.republicate.skorm

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.File
import java.io.FileWriter
import java.util.*

abstract class BaseGenerationTask : DefaultTask() {

    @get:Input
    @get:Option(option = "destPackage", description = "Destination package")
    @get:Optional
    abstract val destPackage: Property<String>

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

    protected open fun generateCode(templatePath: String, destFile: File) {
        logger.info("$tag $templatePath -> $destFile")
        destFile.parentFile.mkdirs()
        val writer = FileWriter(destFile)
        val template = getVelocityEngine().getTemplate(templatePath) ?: throw RuntimeException("template not found")
        template.merge(getVelocityContext(), writer)
        writer.close()
    }
}
