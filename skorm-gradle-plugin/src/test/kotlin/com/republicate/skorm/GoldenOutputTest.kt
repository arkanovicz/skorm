package com.republicate.skorm

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Generated code, compared byte for byte with the expected files beside each fixture: "unchanged", not "correct".
 * Run with SKORM_UPDATE_GOLDEN=1 to rewrite them after an intended change, then review the diff.
 */
class GoldenOutputTest {

    @TempDir
    lateinit var out: File

    private val golden = File("src/test/resources/golden")

    @Test
    fun bookshelf() = check(
        "bookshelf",
        File("../examples/bookshelf/src/commonMain/model/bookshelf.kddl"),
        File("../examples/bookshelf/src/commonMain/model/bookshelf.ksql"),
        "com.republicate.skorm.bookshelf", "hypersql"
    )

    @Test
    fun shapes() = check(
        "shapes",
        golden.resolve("shapes/model.kddl"),
        golden.resolve("shapes/attributes.ksql"),
        "shapes.model", "postgresql"
    )

    private fun check(name: String, model: File, attributes: File, destPackage: String, dialect: String) {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.republicate.skorm")
        (project.extensions.getByName(EXTENSION_NAME) as SkormParams).also {
            it.model.set(model.absoluteFile)
            it.attributes.set(attributes.absoluteFile)
            it.destPackage.set(destPackage)
            it.dialect.set(dialect)
            // every output, whatever the targets
            it.core.set(true)
            it.client.set(true)
            it.outputDirectory.set(out)
        }
        (project.tasks.getByName(GEN_TASK_NAME) as GenerateSkormCodeTask).generate()

        val expected = golden.resolve("$name/expected")
        val actual = out.filesByPath()
        if (System.getenv("SKORM_UPDATE_GOLDEN") != null) {
            expected.deleteRecursively()
            actual.forEach { (path, file) -> file.copyTo(expected.resolve(path)) }
            return
        }
        val wanted = expected.filesByPath()
        assertEquals(wanted.keys.sorted(), actual.keys.sorted(), "$name: generated files")
        for ((path, file) in wanted) assertEquals(file.readText(), actual.getValue(path).readText(), "$name: $path")
    }

    private fun File.filesByPath() =
        walk().filter { it.isFile }.associateBy { it.relativeTo(this).invariantSeparatorsPath }
}
