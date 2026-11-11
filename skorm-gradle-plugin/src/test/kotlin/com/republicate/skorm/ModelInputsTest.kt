package com.republicate.skorm

import com.republicate.kddl.SearchPathResolver
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The model as the input of generation: found with the files it includes or imports, read again on every run, watched. */
class ModelInputsTest {

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

    private val sql get() = File(dir, "build/generated-src/core/resources/META-INF/skorm/root.sql").readText()
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

    @Test
    fun `under the configuration cache the model is read again, not kept as first parsed`() {
        assertEquals(TaskOutcome.SUCCESS, generate("--configuration-cache"))
        write("model/root.kddl", File(dir, "model/root.kddl").readText().replace("table doc {", "table memo { *id serial }\n    table doc {"))
        val run = runner("--configuration-cache").build()
        assertContains(run.output, "Reusing configuration cache.")
        assertEquals(TaskOutcome.SUCCESS, run.task(":$GEN_TASK_NAME")?.outcome)
        assertContains(sql, "CREATE TABLE memo (")
        assertContains(objects, "interface Memo")
    }

    private fun assertIncludedFilesWatched(vararg args: String) {
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertContains(sql, "CREATE TABLE person (")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))

        write("model/shared.kddl", """
            database shared {
              schema core {
                table person { *id serial }
                table team { *id serial }
              }
            }
        """)
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertContains(sql, "CREATE TABLE team (")
        assertContains(objects, "interface Team")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))
    }

    @Test
    fun `editing an included model reruns generation`() = assertIncludedFilesWatched()

    @Test
    fun `with the configuration cache too, which must not freeze the list of includes`() {
        assertIncludedFilesWatched("--configuration-cache")

        write("model/extra.kddl", """
            database extra {
              schema core {
                table tag { *id serial }
              }
            }
        """)
        write("model/root.kddl", "include 'extra.kddl'\n" + File(dir, "model/root.kddl").readText())
        val run = runner("--configuration-cache").build()
        assertContains(run.output, "Reusing configuration cache.")
        assertEquals(TaskOutcome.SUCCESS, run.task(":$GEN_TASK_NAME")?.outcome)
        assertContains(sql, "CREATE TABLE tag (")
        assertEquals(TaskOutcome.UP_TO_DATE, generate("--configuration-cache"))

        // a file the cached configuration never heard of is watched all the same
        write("model/extra.kddl", """
            database extra {
              schema core {
                table tag { *id serial }
                table label { *id serial }
              }
            }
        """)
        assertEquals(TaskOutcome.SUCCESS, generate("--configuration-cache"))
        assertContains(sql, "CREATE TABLE label (")
    }

    private fun arkad(key: String) = """
        database arkad {
          schema arkad {
            table message { *id $key }
          }
        }
    """

    private fun importing(roots: String) {
        write("build.gradle.kts", """
            plugins {
                id("com.republicate.skorm")
            }
            skorm {
                model.set(file("model/root.kddl"))
                imports.from($roots)
                destPackage.set("sample.model")
                dialect.set("hypersql")
                core.set(true)
            }
        """)
        write("model/root.kddl", """
            import 'arkad.kddl'
            database app {
              schema app {
                table decision {
                  *id serial
                  message -> arkad.message
                }
              }
            }
        """)
    }

    // what the artifact of the imported kddl schema says of its generated code: an import is not generated without it
    private val arkadInfo = "database=arkad\nimports=\npackage=arkad.model\nreadOnly=false\nschemas=arkad\n"

    /** a jar shipping [model] where an artifact does, with its schema info */
    private fun jar(path: String, model: String) = File(dir, path).apply {
        parentFile.mkdirs()
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${SearchPathResolver.ARTIFACT_DIR}/arkad.kddl"))
            zip.write(model.trimIndent().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${SchemaInfo.DIR}/arkad.properties"))
            zip.write(arkadInfo.toByteArray())
            zip.closeEntry()
        }
    }

    /** nothing of the imported schema is created or generated: its table is referenced, its class named */
    private fun assertImportedOnly() {
        assertContains(sql, "CREATE TABLE decision (")
        assertContains(sql, "REFERENCES arkad.message")
        assertFalse("CREATE TABLE message (" in sql, sql)
        assertContains(objects, "interface Decision")
        assertContains(objects, "suspend fun message(): arkad.model.ArkadDatabase.ArkadSchema.Message")
        assertFalse("interface Message" in objects, objects)
    }

    private fun assertJarWatched(vararg args: String) {
        importing("\"libs/arkad.jar\"")
        jar("libs/arkad.jar", arkad("serial"))
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertImportedOnly()
        assertContains(sql, "message integer")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))

        // the key type of the imported table types the column
        jar("libs/arkad.jar", arkad("bigserial"))
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertContains(sql, "message bigint")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))
    }

    private fun assertDirectoryWatched(vararg args: String) {
        importing("\"lib\"")
        write("lib/arkad.kddl", arkad("serial"))
        write("lib/${SchemaInfo.DIR}/arkad.properties", arkadInfo)
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertImportedOnly()
        assertContains(sql, "message integer")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))

        write("lib/arkad.kddl", arkad("bigserial"))
        assertEquals(TaskOutcome.SUCCESS, generate(*args))
        assertContains(sql, "message bigint")
        assertEquals(TaskOutcome.UP_TO_DATE, generate(*args))
    }

    @Test
    fun `a kddl schema imported from a jar, which is watched`() = assertJarWatched()

    @Test
    fun `a kddl schema imported from a jar, under the configuration cache`() = assertJarWatched("--configuration-cache")

    @Test
    fun `a kddl schema imported from a directory, which is watched`() = assertDirectoryWatched()

    @Test
    fun `a kddl schema imported from a directory, under the configuration cache`() = assertDirectoryWatched("--configuration-cache")
}
