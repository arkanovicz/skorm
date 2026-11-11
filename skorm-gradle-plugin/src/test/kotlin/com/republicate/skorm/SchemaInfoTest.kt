package com.republicate.skorm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The meta-info an artifact ships for each kddl schema, read back from directories and jars. */
class SchemaInfoTest {

    @TempDir
    lateinit var dir: File

    private val arkad = SchemaInfo("arkad", "org.arkad.model", "arkad", listOf("arkad", "auth"), false, emptyList())
    private val republic = SchemaInfo("republic", "org.republic.model", "republicate", listOf("republic"), true, listOf("arkad.kddl", "lib/other.kddl"))

    private fun SchemaInfo.fields() = listOf(base, destPackage, database, schemas, readOnly, imports)

    private fun assertSame(expected: List<SchemaInfo>, actual: List<SchemaInfo>) =
        assertEquals(expected.map { it.fields() }, actual.map { it.fields() })

    /** a jar holding what [resources] holds */
    private fun jar(resources: File, jar: File) = jar.also {
        ZipOutputStream(it.outputStream()).use { zip ->
            resources.walk().filter { file -> file.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(resources).invariantSeparatorsPath))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `read back from a directory`() {
        val root = File(dir, "classes")
        republic.write(root)
        assertSame(listOf(republic), SchemaInfo.read(listOf(root)))
    }

    @Test
    fun `read back from a jar`() {
        val root = File(dir, "classes")
        republic.write(root)
        assertSame(listOf(republic), SchemaInfo.read(listOf(jar(root, File(dir, "republic.jar")))))
    }

    @Test
    fun `several in one root, and several roots`() {
        val first = File(dir, "first")
        republic.write(first)
        arkad.write(first)
        val second = File(dir, "second")
        arkad.write(second)
        assertSame(listOf(arkad, republic), SchemaInfo.read(listOf(first)))
        assertSame(listOf(arkad, republic, arkad), SchemaInfo.read(listOf(first, jar(second, File(dir, "second.jar")))))
    }

    @Test
    fun `a root that is no archive is skipped`() {
        val root = File(dir, "classes")
        arkad.write(root)
        val notAJar = File(dir, "notes.txt").apply { writeText("not a zip") }
        assertSame(listOf(arkad), SchemaInfo.read(listOf(notAJar, root, File(dir, "missing"))))
    }

    @Test
    fun `the written file is byte-stable`() {
        val file = File(dir, "${SchemaInfo.DIR}/republic.properties")
        republic.write(dir)
        val first = file.readBytes()
        Thread.sleep(1100) // a date, if any, would change by the second
        republic.write(dir)
        assertEquals(first.toString(Charsets.UTF_8), file.readText())
        assertEquals(
            """
                database=republicate
                imports=arkad.kddl,lib/other.kddl
                package=org.republic.model
                readOnly=true
                schemas=republic
            """.trimIndent() + "\n",
            file.readText()
        )
    }
}
