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

abstract class GenerateJoinAttributesCodeTask : GenerateTask() {

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
