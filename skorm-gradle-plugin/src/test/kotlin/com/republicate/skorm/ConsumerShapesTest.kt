package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Consumer layouts the plugin must wire, beyond the bookshelf example's own. */
class ConsumerShapesTest {

    @TempDir
    lateinit var dir: File

    /**
     * A root-declared plugin lives in a parent classloader that cannot see a Kotlin plugin applied
     * below it; the plugin must still find the Kotlin source sets.
     */
    @Test
    fun `plugin pinned at the root, Kotlin applied only in a subproject`() {
        val skorm = File("..").canonicalFile.invariantSeparatorsPath
        dir.resolve("settings.gradle.kts").writeText("""
            rootProject.name = "root-pinned"
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); mavenLocal() }
                includeBuild("$skorm")
            }
            dependencyResolutionManagement { repositories { mavenCentral(); mavenLocal() } }
            includeBuild("$skorm")
            include("app")
        """.trimIndent())
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.republicate.skorm") apply false }
        """.trimIndent())
        dir.resolve("app/src/main/model").mkdirs()
        dir.resolve("app/src/main/kotlin").mkdirs()
        dir.resolve("app/build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "2.4.0"
                id("com.republicate.skorm")
            }
            kotlin { jvmToolchain(21) }
            skorm {
                model.set(file("src/main/model/tiny.kddl"))
                destPackage.set("tiny.model")
                dialect.set("hypersql")
            }
            dependencies {
                implementation("com.republicate.skorm:skorm-common")
                implementation("com.republicate.skorm:skorm-core")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.github.oshai:kotlin-logging:8.0.4")
            }
        """.trimIndent())
        dir.resolve("app/src/main/model/tiny.kddl").writeText("""
            database tiny {
              schema tiny {
                table author { name varchar(100) }
                table book { title varchar(100) }
                book *-- author
              }
            }
        """.trimIndent())
        // compiles only if the generated sources reached this source set
        dir.resolve("app/src/main/kotlin/Use.kt").writeText("""
            import tiny.model.*
            suspend fun titles(author: TinyDatabase.TinySchema.Author): List<String> = author.books().map { it.title }.toList()
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(":app:compileKotlin")
            .forwardOutput()
            .build()
        Assertions.assertTrue(
            result.task(":app:compileKotlin")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        )
    }
}
