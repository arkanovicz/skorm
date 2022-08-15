package com.republicate.skorm

import org.gradle.api.tasks.*

abstract class GenerateObjectsCodeTask : BaseStructureGenerationTask() {

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
