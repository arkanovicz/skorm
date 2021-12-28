package com.republicate.skorm

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

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

        destFile.get().asFile.writeText("$tag Test ok")
    }
}
