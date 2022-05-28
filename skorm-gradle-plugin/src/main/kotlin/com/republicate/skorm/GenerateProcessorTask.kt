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

abstract class GenerateProcessorTask : GenerateTask() {

    init {
        description = "Skorm queries generation in core processor"
        group = "code generation"
    }

    override val templatePath = "templates/skorm-processor.vtl"

    @TaskAction
    fun populateQueries() {
        generateCode()
    }
}
