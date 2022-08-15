package com.republicate.skorm

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option

abstract class GenerateJoinAttributesCodeTask : BaseStructureGenerationTask() {

    init {
        description = "Skorm code generation of database joins attributes"
        group = "code generation"
    }

    @get:Option(option = "destCoreFile", description = "Destination file for core")
    @get:OutputFile
    abstract val destCoreFile: RegularFileProperty

    @get:Option(option = "destClientFile", description = "Destination file for client")
    @get:OutputFile
    abstract val destClientFile: RegularFileProperty

    private val templatePath = "templates/skorm-joins.vtl"
    private val templateCorePath = "templates/skorm-joins-core.vtl"
    private val templateClientPath = "templates/skorm-joins-client.vtl"

    @TaskAction
    fun generateJoinsCode() {
        generateCode(templatePath, destFile)
        generateCode(templateCorePath, destCoreFile)
        generateCode(templateClientPath, destClientFile)
    }
}
