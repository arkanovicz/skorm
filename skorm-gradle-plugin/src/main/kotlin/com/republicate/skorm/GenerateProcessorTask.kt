package com.republicate.skorm

import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.FileWriter

abstract class GenerateProcessorTask : DefaultTask() {

    init {
        description = "Skorm queries generation in core processor"
        group = "code generation"
    }

    override fun dependsOn(vararg paths: Any?): Task? {
        return super.dependsOn(*paths)
    }

    @get:InputFile
    @get:Option(option = "modelStructure", description = "Source kddl model structure")
    @get:Optional
    abstract val modelStructure: RegularFileProperty

    @get:Input
    @get:Option(option = "datasource", description = "Datasource JDBC URL with credentials")
    @get:Optional
    abstract val datasource: Property<String>

    @get:Input
    @get:Option(option = "destPackage", description = "Destination package")
    @get:Optional
    abstract val destPackage: Property<String>

    @get:OutputFile
    abstract val destPopulateFile: RegularFileProperty

    @TaskAction
    fun populateQueries() {
        val tag = "[skorm]"

        logger.lifecycle("$tag modelStructure is: ${modelStructure.orNull}")
        logger.lifecycle("$tag datasource is: ${datasource.orNull}")
        logger.lifecycle("$tag destPackage is: ${destPackage.orNull}")
        logger.lifecycle("$tag destPopulateFile is: ${destPopulateFile.orNull}")

        val foundStructure = modelStructure.orNull
        val foundDatasource = datasource.orNull

        if ((foundStructure == null) == (foundDatasource == null)) {
            throw RuntimeException("$tag expecting skorm.structure or skorm.datasource")
        }

        val database = when {
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

        val engine = Velocity.engine
        val context = Velocity.getContext().apply {
            put("package", destPackage.get())
            put("database", database)
        }
        val writer = FileWriter(destPopulateFile.get().asFile)
        val template = engine.getTemplate("templates/skorm-processor.vtl") ?: throw RuntimeException("template not found")
        template.merge(context, writer)
        writer.close()
    }
}
