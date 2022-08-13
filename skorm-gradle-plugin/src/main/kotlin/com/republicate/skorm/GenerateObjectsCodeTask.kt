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

abstract class GenerateObjectsCodeTask : GenerateTask() {

    init {
        description = "Skorm code generation of database objects classes"
        group = "code generation"
    }

    private val templatePath = "templates/skorm-objects.vtl"

    @TaskAction
    fun generateObjectsCode() {
        generateCode(templatePath, destFile)
    }
}
