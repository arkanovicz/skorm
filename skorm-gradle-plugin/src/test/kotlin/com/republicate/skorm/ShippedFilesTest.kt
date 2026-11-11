package com.republicate.skorm

import com.republicate.kddl.SearchPathResolver
import com.republicate.kddl.Utils
import com.republicate.kddl.parse
import com.republicate.kddl.postgresql.PostgreSQLFormatter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What generation ships for another kddl schema to import this one. */
class ShippedFilesTest {

    @TempDir
    lateinit var dir: File

    private val models get() = File(dir, "model")
    private val out get() = File(dir, "out")

    private fun write(path: String, content: String) =
        File(models, path).apply { parentFile?.mkdirs() }.writeText(content.trimIndent())

    private fun generate(readOnly: Boolean = false) {
        write("root.kddl", """
            import 'arkad.kddl'
            include 'shared.kddl'
            database app {
              schema app {
                table doc {
                  *id serial
                  owner -> core.person
                  message -> arkad.message
                }
              }
            }
        """)
        write("shared.kddl", """
            database shared {
              schema core {
                table person { *id serial }
              }
            }
        """)
        write("arkad.kddl", """
            database arkad {
              schema arkad {
                table message { *id serial }
              }
            }
        """)
        generateInto(out, File(models, "root.kddl"), null, "sample.model", "postgresql", readOnly)
    }

    private val kddl get() = File(out, "common/resources/${SearchPathResolver.ARTIFACT_DIR}/root.kddl")
    private val script get() = File(out, "core/resources/${SchemaInfo.script("root")}")

    @Test
    fun `the kddl schema ships flattened, and reads back as the same model`() {
        generate()
        val flat = kddl.readText()
        assertFalse("include" in flat, flat)
        assertTrue("import 'arkad.kddl'" in flat, flat)
        assertTrue("table person" in flat, flat)
        assertTrue("table doc" in flat, flat)
        assertFalse("table message" in flat, flat)

        // its import is looked up along a search path, as an importer would
        val again = parse(Utils.getFile(kddl.absolutePath), resolver = SearchPathResolver(listOf(models)))
        assertEquals(script.readText(), PostgreSQLFormatter(quoted = false, uppercase = false).format(again))
    }

    @Test
    fun `the creation script is named after the kddl schema, off the classpath root`() {
        generate()
        assertTrue(script.isFile)
        assertFalse(File(out, "core/resources/create-script.sql").exists())
    }

    @Test
    fun `the schema info names the package, the database, the schemas it owns and its imports`() {
        generate(readOnly = true)
        val info = SchemaInfo.read(listOf(File(out, "common/resources"))).single()
        assertEquals("root", info.base)
        assertEquals("sample.model", info.destPackage)
        assertEquals("app", info.database)
        assertEquals(setOf("app", "core"), info.schemas.toSet())
        assertTrue(info.readOnly)
        assertEquals(listOf("arkad.kddl"), info.imports)
    }
}
