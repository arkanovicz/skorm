package com.republicate.skorm

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "skorm"
const val GEN_OBJECTS_TASK_NAME = "generateSkormObjectsCode"
const val GEN_PROPERTIES_TASK_NAME = "generateSkormPropertiesCode"
const val POPULATE_CORE_PROCESSOR_TASK_NAME = "generateSkormProcessor"

abstract class SkormGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Add the 'template' extension object
        val extension = project.extensions.create(EXTENSION_NAME, SkormParams::class.java, project)

        // Generate database objects
        project.tasks.register(GEN_OBJECTS_TASK_NAME, GenerateObjectsCodeTask::class.java) {
            it.modelStructure.set(extension.modelStructure)
            it.datasource.set(extension.datasource)
            it.destPackage.set(extension.destPackage)
            it.destStructureFile.set(extension.destStructureFile)
        }

        // Populate queries in core processor
        project.tasks.register(POPULATE_CORE_PROCESSOR_TASK_NAME, GenerateProcessorTask::class.java) {
            it.modelStructure.set(extension.modelStructure)
            it.datasource.set(extension.datasource)
            it.destPackage.set(extension.destPackage)
            it.destPopulateFile.set(extension.destPopulateFile)
        }

        // Generate database properties
        project.tasks.register(GEN_PROPERTIES_TASK_NAME, GeneratePropertiesCodeTask::class.java) {
            it.destPackage.set(extension.destPackage)
            it.destPropertiesFile.set(extension.destPropertiesFile)
        }
    }
}
