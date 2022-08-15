package com.republicate.skorm

import com.republicate.kddl.hypersql.HyperSQLFormatter
import org.gradle.api.tasks.*
import java.io.FileWriter

abstract class GenerateCreationScriptTask : BaseStructureGenerationTask() {

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

        writer.write(HyperSQLFormatter(quoted=false, uppercase=false).format(database))

        writer.flush()
        writer.close()
    }
}
