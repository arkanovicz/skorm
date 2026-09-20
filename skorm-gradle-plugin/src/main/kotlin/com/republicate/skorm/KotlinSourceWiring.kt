package com.republicate.skorm

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer

/** Everything that touches Kotlin Gradle plugin types, loaded only once one is applied. */
internal object KotlinSourceWiring {

    private const val COMMON = "common"
    private const val CORE = "core"
    private const val CLIENT = "client"

    fun wireMultiplatform(project: Project, generate: TaskProvider<GenerateSkormCodeTask>) {
        val sourceSets = project.sourceSets() ?: return
        val targets = (project.extensions.findByName("kotlin") as? KotlinTargetsContainer)?.targets ?: return
        sourceSets.named("commonMain") { it.kotlin.srcDir(generate.dir(COMMON, "kotlin")) }
        targets.all { target ->
            val platform = target.platformType.name
            if (platform == "common") return@all
            generate.configure { it.platforms.add(platform) }
            val role = if (platform in GenerateSkormCodeTask.JVM_PLATFORMS) CORE else CLIENT
            sourceSets.named("${target.name}Main") {
                it.kotlin.srcDir(generate.dir(role, "kotlin"))
                if (role == CORE) it.resources.srcDir(generate.dir(role, "resources"))
            }
        }
    }

    /** A plain `kotlin("jvm")` project: one source set holds the common and the server halves. */
    fun wireSingleTarget(project: Project, generate: TaskProvider<GenerateSkormCodeTask>) {
        val sourceSets = project.sourceSets() ?: return
        generate.configure { it.platforms.add("jvm") }
        sourceSets.named("main") {
            it.kotlin.srcDir(generate.dir(COMMON, "kotlin"))
            it.kotlin.srcDir(generate.dir(CORE, "kotlin"))
            it.resources.srcDir(generate.dir(CORE, "resources"))
        }
    }

    private fun Project.sourceSets(): NamedDomainObjectContainer<KotlinSourceSet>? =
        (extensions.findByName("kotlin") as? KotlinSourceSetContainer)?.sourceSets

    /** A provider Gradle can trace back to the generating task. */
    private fun TaskProvider<GenerateSkormCodeTask>.dir(role: String, kind: String) =
        flatMap { it.outputDirectory.dir("$role/$kind") }
}
