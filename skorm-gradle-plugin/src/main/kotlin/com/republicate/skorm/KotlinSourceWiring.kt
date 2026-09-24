package com.republicate.skorm

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.TaskProvider

/**
 * Registers the generated directories on the consumer's Kotlin source sets.
 *
 * Deliberately touches no Kotlin Gradle plugin type: skorm may be declared in a parent project whose
 * classloader cannot see a Kotlin plugin applied below it. Everything goes through the Kotlin extension's
 * public accessors by name, and every value used is a Gradle or JDK type.
 */
internal object KotlinSourceWiring {

    private const val COMMON = "common"
    private const val CORE = "core"
    private const val CLIENT = "client"

    fun wireMultiplatform(project: Project, generate: TaskProvider<GenerateSkormCodeTask>) = reaching(project) {
        val kotlin = project.extensions.getByName("kotlin")
        val sourceSets = kotlin.sourceSets()
        generate.configure { it.multiplatform.set(true) }
        sourceSets.named("commonMain") { it.kotlinDir().srcDir(generate.dir(COMMON, "kotlin")) }
        @Suppress("UNCHECKED_CAST")
        (kotlin.invoke("getTargets") as NamedDomainObjectCollection<Any>).all { target ->
            val platform = (target.invoke("getPlatformType") as Enum<*>).name
            if (platform == "common") return@all
            generate.configure { it.platforms.add(platform) }
            val role = if (platform in GenerateSkormCodeTask.JVM_PLATFORMS) CORE else CLIENT
            sourceSets.named("${(target as Named).name}Main") {
                it.kotlinDir().srcDir(generate.dir(role, "kotlin"))
                if (role == CORE) it.resourcesDir().srcDir(generate.dir(role, "resources"))
            }
        }
    }

    /** A plain `kotlin("jvm")` project: one source set holds the common and the server halves. */
    fun wireSingleTarget(project: Project, generate: TaskProvider<GenerateSkormCodeTask>) = reaching(project) {
        generate.configure { it.platforms.add("jvm") }
        project.extensions.getByName("kotlin").sourceSets().named("main") {
            it.kotlinDir().srcDir(generate.dir(COMMON, "kotlin"))
            it.kotlinDir().srcDir(generate.dir(CORE, "kotlin"))
            it.resourcesDir().srcDir(generate.dir(CORE, "resources"))
        }
    }

    // a failure here would otherwise surface as generated classes the consumer can't resolve
    private fun reaching(project: Project, wiring: () -> Unit) = try {
        wiring()
    } catch (e: ReflectiveOperationException) {
        throw GradleException("skorm: cannot reach the Kotlin source sets of '${project.path}' ($e)", e)
    } catch (e: ClassCastException) {
        throw GradleException("skorm: unexpected Kotlin extension shape in '${project.path}' ($e)", e)
    }

    private fun Any.invoke(getter: String): Any = javaClass.getMethod(getter).invoke(this)

    @Suppress("UNCHECKED_CAST")
    private fun Any.sourceSets() = invoke("getSourceSets") as NamedDomainObjectContainer<Any>

    private fun Any.kotlinDir() = invoke("getKotlin") as SourceDirectorySet

    private fun Any.resourcesDir() = invoke("getResources") as SourceDirectorySet

    /** A provider Gradle can trace back to the generating task. */
    private fun TaskProvider<GenerateSkormCodeTask>.dir(role: String, kind: String) =
        flatMap { it.outputDirectory.dir("$role/$kind") }
}
