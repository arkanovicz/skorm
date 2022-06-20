package com.republicate.skorm

import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.hypersql.HyperSQLFormatter
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import java.io.FileWriter

abstract class GenerateCreationScriptTask : GenerateTask() {

    init {
        description = "Skorm SQL code generation of database creation script"
        group = "code generation"
    }

    @TaskAction
    fun generateCreationScript() {
        logger.lifecycle("$tag modelStructure is: ${model.orNull}")
        logger.lifecycle("$tag destCreationScriptFile is: ${destFile.orNull}")

        destFile.get().asFile.parentFile.mkdirs()
        val writer = FileWriter(destFile.get().asFile)

        writer.write(HyperSQLFormatter(quoted=false, uppercase=false).format(database))

        writer.flush()
        writer.close()
    }
}
