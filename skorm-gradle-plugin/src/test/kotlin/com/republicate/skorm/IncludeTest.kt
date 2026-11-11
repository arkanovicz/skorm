package com.republicate.skorm

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** A model split over several files: the included ones are found beside the including one, and watched. */
class IncludeTest {

    @TempDir
    lateinit var dir: File

    @BeforeEach
    fun setUp() {
        write("settings.gradle.kts", """rootProject.name = "sample"""")
        write("build.gradle.kts", """
            plugins {
                id("com.republicate.skorm")
            }
            skorm {
                model.set(file("model/root.kddl"))
                destPackage.set("sample.model")
                dialect.set("hypersql")
                core.set(true)
            }
        """)
        // the build runs from the project directory: `shared.kddl` is only found beside its includer
        write("model/root.kddl", """
            include 'shared.kddl'
            database app {
              schema app {
                table doc {
                  *id serial
                  owner -> core.person
                }
              }
            }
        """)
        write("model/shared.kddl", """
            database shared {
              schema core {
                table person { *id serial }
              }
            }
        """)
    }

    private fun write(path: String, content: String) =
        File(dir, path).apply { parentFile?.mkdirs() }.writeText(content.trimIndent())

    private fun runner(vararg args: String) =
        GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(GEN_TASK_NAME, *args)

    private fun generate(vararg args: String): TaskOutcome? =
        runner(*args).build().task(":$GEN_TASK_NAME")?.outcome

    private val sql get() = File(dir, "build/generated-src/core/resources/create-script.sql").readText()
    private val objects get() = File(dir, "build/generated-src/common/kotlin/skormObjects.kt").readText()

    private fun assertContains(text: String, part: String) = assertTrue(part in text) { "expected `$part` in:\n$text" }

    @Test
    fun `an included model is found beside the including one, and generated with it`() {
        assertEquals(TaskOutcome.SUCCESS, generate())
        assertContains(sql, "CREATE TABLE person (")
        assertContains(sql, "CREATE TABLE doc (")
        assertContains(objects, "interface Person")
        assertContains(objects, "interface Doc")
    }
}
