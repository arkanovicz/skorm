package com.republicate.skorm

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "skorm"
const val TASK_NAME = "skormCodeGeneration"

abstract class SkormGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Add the 'template' extension object
        val extension = project.extensions.create(EXTENSION_NAME, CodeGenParams::class.java, project)

        // Add a task that uses configuration from the extension object
        project.tasks.register(TASK_NAME, CodeGenTask::class.java) {
            it.source.set(extension.source)
            it.destPackage.set(extension.destPackage)
            it.destFile.set(extension.destFile)
        }
    }
}
