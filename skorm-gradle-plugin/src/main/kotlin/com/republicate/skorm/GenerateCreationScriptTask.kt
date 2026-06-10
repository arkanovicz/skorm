package com.republicate.skorm

import com.republicate.kddl.Formatter
import com.republicate.kddl.hypersql.HyperSQLFormatter
import com.republicate.kddl.postgresql.PostgreSQLFormatter
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.FileWriter

abstract class GenerateCreationScriptTask : BaseStructureGenerationTask() {

    @get:Input
    @get:Optional
    abstract val dialect: Property<String>

    init {
        description = "Skorm SQL code generation of database creation script"
        group = "code generation"
    }

    @TaskAction
    fun generateCreationScript() {
        logger.lifecycle("$tag modelStructure is: ${structure.orNull}")
        logger.lifecycle("$tag destCreationScriptFile is: ${destFile.orNull}")

        destFile.get().asFile.parentFile.mkdirs()
        val writer = FileWriter(destFile.get().asFile)

        val formatter: Formatter = when (val d = dialect.orNull?.lowercase()) {
            "postgresql" -> PostgreSQLFormatter(quoted = false, uppercase = false)
            "hypersql" -> HyperSQLFormatter(quoted = false, uppercase = false)
            null -> throw org.gradle.api.GradleException(
                "skorm: no SQL dialect configured. Set `skorm { dialect = \"...\" }` to one of: postgresql, hypersql")
            else -> throw org.gradle.api.GradleException(
                "skorm: unknown SQL dialect '$d'. Valid dialects: postgresql, hypersql")
        }
        writer.write(formatter.format(database))

        writer.flush()
        writer.close()
    }
}
