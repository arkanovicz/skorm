package com.republicate.skorm

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "skorm"
const val GEN_OBJECTS_TASK_NAME = "generateSkormObjectsCode"
const val GEN_JOINS_ATTRIBUTES_NAME = "generateSkormJoinsCode"
const val GEN_RUNTIME_MODEL_NAME = "generateSkormModelCode"
const val GEN_DATABASE_CREATION_SCRIPT_TASK_NAME = "generateDatabaseCreationScript"

abstract class SkormGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {

        // Add the 'template' extension object
        val extension = project.extensions.create(EXTENSION_NAME, SkormParams::class.java, project)

        // Generate database objects
        project.tasks.register(GEN_OBJECTS_TASK_NAME, GenerateObjectsCodeTask::class.java) {
            it.structure.set(extension.structure)
            it.datasource.set(extension.datasource)
            it.destPackage.set(extension.destPackage)
            it.destFile.set(extension.destStructureFile)
        }

        // Generate tables joins attributes
        project.tasks.register(GEN_JOINS_ATTRIBUTES_NAME, GenerateJoinAttributesCodeTask::class.java) {
            it.structure.set(extension.structure)
            it.datasource.set(extension.datasource)
            it.destPackage.set(extension.destPackage)
            it.destFile.set(extension.destJoinsFile)
            it.destCoreFile.set(extension.destCoreJoinsFile)
            it.destClientFile.set(extension.destClientJoinsFile)
        }

        // Generate runtime model objects and attributes
        project.tasks.register(GEN_RUNTIME_MODEL_NAME, GenerateRuntimeModelTask::class.java) {
            it.structure.set(extension.structure)
            it.datasource.set(extension.datasource)
            it.runtimeModel.set(extension.runtimeModel)
            it.destPackage.set(extension.destPackage)
            it.destFile.set(extension.destModelFile)
            it.destCoreFile.set(extension.destCoreModelFile)
            it.destClientFile.set(extension.destClientModelFile)
        }


//        // Populate queries in core processor
//        project.tasks.register(POPULATE_CORE_PROCESSOR_TASK_NAME, GenerateProcessorTask::class.java) {
//            it.model.set(extension.definition)
//            it.datasource.set(extension.datasource)
//            it.destPackage.set(extension.destPackage)
//            it.destFile.set(extension.destPopulateFile)
//        }
//
//        // Generate database properties
//        project.tasks.register(GEN_PROPERTIES_TASK_NAME, GeneratePropertiesCodeTask::class.java) {
//            it.model.set(extension.definition)
//            it.datasource.set(extension.datasource)
//            it.propertiesFile.set(extension.properties)
//            it.destPackage.set(extension.destPackage)
//            it.destFile.set(extension.destPropertiesFile)
//        }
//
        // Generate database creation script
        project.tasks.register(GEN_DATABASE_CREATION_SCRIPT_TASK_NAME, GenerateCreationScriptTask::class.java) {
            it.structure.set(extension.structure)
            it.destFile.set(extension.destCreationScriptFile)
            it.dialect.set(extension.dialect)
        }
    }
}
