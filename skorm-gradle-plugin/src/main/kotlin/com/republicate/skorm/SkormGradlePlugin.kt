package com.republicate.skorm

import org.gradle.api.Plugin
import org.gradle.api.Project

const val EXTENSION_NAME = "skorm"
const val GEN_TASK_NAME = "generateSkormCode"

const val KOTLIN_MULTIPLATFORM_PLUGIN = "org.jetbrains.kotlin.multiplatform"
const val KOTLIN_JVM_PLUGIN = "org.jetbrains.kotlin.jvm"

abstract class SkormGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val extension = project.extensions.create(EXTENSION_NAME, SkormParams::class.java, project)

        val generate = project.tasks.register(GEN_TASK_NAME, GenerateSkormCodeTask::class.java) {
            it.model.set(extension.model)
            it.datasource.set(extension.datasource)
            it.attributes.set(extension.attributes)
            it.destPackage.set(extension.destPackage)
            it.dialect.set(extension.dialect)
            it.core.set(extension.core)
            it.client.set(extension.client)
            it.readOnly.set(extension.readOnly)
            it.outputDirectory.set(extension.outputDirectory)
        }

        // Registering the generated dirs on the Kotlin source sets is what lets Gradle sequence
        // generation before compilation by itself.
        project.plugins.withId(KOTLIN_MULTIPLATFORM_PLUGIN) { KotlinSourceWiring.wireMultiplatform(project, generate) }
        project.plugins.withId(KOTLIN_JVM_PLUGIN) { KotlinSourceWiring.wireSingleTarget(project, generate) }
    }
}
