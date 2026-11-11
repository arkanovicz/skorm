package com.republicate.skorm

import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * What the artifact of a kddl schema says of the code generated from it, so that another kddl schema can
 * import it and reach its classes: `META-INF/skorm/<base>.properties`, beside the creation script
 * `META-INF/skorm/<base>.sql`. The kddl schema itself is in `META-INF/kddl/<base>.kddl`, kddl's own convention.
 */
class SchemaInfo(
    /** the kddl file's name without its extension: it names the files the artifact ships */
    val base: String,
    val destPackage: String,
    /** the kddl database name, which the generated database classes are named after */
    val database: String,
    /** the database schemas this kddl schema owns, by their SQL names */
    val schemas: List<String>,
    /** only the read-only half was generated */
    val readOnly: Boolean,
    /** the kddl schemas it imports, as it wrote them */
    val imports: List<String>
) {
    /** [resources]: a resource root, the files land under [DIR] */
    fun write(resources: File) {
        val properties = Properties().apply {
            setProperty(PACKAGE, destPackage)
            setProperty(DATABASE, database)
            setProperty(SCHEMAS, schemas.joinToString(","))
            setProperty(READ_ONLY, readOnly.toString())
            setProperty(IMPORTS, imports.joinToString(","))
        }
        // Properties.store dates its output and orders it by hash: neither belongs in a build output
        val lines = StringWriter().also { properties.store(it, null) }.toString().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.sorted()
        File(resources, "$DIR/$base.properties").apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val DIR = "META-INF/skorm"
        private const val PACKAGE = "package"
        private const val DATABASE = "database"
        private const val SCHEMAS = "schemas"
        private const val READ_ONLY = "readOnly"
        private const val IMPORTS = "imports"

        /** the resource path of the creation script of the kddl schema named [base] */
        fun script(base: String) = "$DIR/$base.sql"

        /** Every kddl schema the [roots], directories and jars, ship generated code for. */
        fun read(roots: Iterable<File>): List<SchemaInfo> = roots.flatMap { root ->
            when {
                root.isDirectory -> File(root, DIR).listFiles { file -> file.isFile && file.name.endsWith(".properties") }
                    .orEmpty().sortedBy { it.name }.map { parse(it.nameWithoutExtension, it.readText()) }
                root.isFile -> try {
                    ZipFile(root).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.startsWith("$DIR/") && it.name.endsWith(".properties") }
                            .sortedBy { it.name }
                            .map { parse(it.name.substringAfterLast('/').removeSuffix(".properties"), zip.getInputStream(it).readBytes().toString(Charsets.UTF_8)) }
                            .toList()
                    }
                } catch (e: ZipException) { emptyList() } // a search path built from a configuration may hold files that are no archive
                else -> emptyList()
            }
        }

        private fun parse(base: String, text: String): SchemaInfo {
            val properties = Properties().apply { load(StringReader(text)) }
            fun value(key: String) = properties.getProperty(key) ?: throw SkormException("$DIR/$base.properties: no $key")
            fun list(key: String) = value(key).split(',').filter { it.isNotEmpty() }
            return SchemaInfo(base, value(PACKAGE), value(DATABASE), list(SCHEMAS), value(READ_ONLY).toBoolean(), list(IMPORTS))
        }
    }
}
