package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.apache.velocity.VelocityContext
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option

abstract class BaseStructureGenerationTask: BaseGenerationTask() {

    @get:InputFile
    @get:Option(option = "modelStructure", description = "Source kddl model structure")
    @get:Optional
    abstract val structure: RegularFileProperty

    @get:Input
    @get:Option(option = "datasource", description = "Datasource JDBC URL with credentials")
    @get:Optional
    abstract val datasource: Property<String>

    @get:Internal
    protected val database: ASTDatabase by lazy {
        val foundStructure = structure.orNull
        val foundDatasource = datasource.orNull
        logger.lifecycle("$tag @@@ found datasource: $foundDatasource")
        logger.lifecycle("$tag @@@ found structure: $foundStructure")
        if ((foundStructure == null) == (foundDatasource == null)) {
            throw RuntimeException("$tag expecting exactly one of skorm.structure or skorm.datasource parameter")
        }
        val db = when {
            foundStructure != null -> {
                val file = project.file(foundStructure) ?: throw RuntimeException("model structure file not found")
                val ddl = Utils.getFile(file.absolutePath)
                parse(ddl)
            }
            foundDatasource != null -> {
                reverse(foundDatasource)
            }
            else -> throw RuntimeException("this cannot happen")
        }
        db
    }

    override fun populateContext(context: VelocityContext) {
        val usedTypes = database.schemas.values
            .flatMap { schema: ASTSchema -> schema.tables.values }
            .flatMap { table -> table.fields.values }
            .map { type -> type.type}
            .toSet()
        context.put("database", database)
        context.put("datetimes", usedTypes.contains("timestamp") || usedTypes.contains("timestamp_tz") || usedTypes.contains("time") || usedTypes.contains("time_tz"))
        context.put("uuids", usedTypes.contains("uuid"))
    }

    override fun generateCode(templatePath: String, destFile: RegularFileProperty) {
        logger.lifecycle("$tag modelStructure is: ${structure.orNull}")
        logger.lifecycle("$tag datasource is: ${datasource.orNull}")
        super.generateCode(templatePath, destFile)
    }

}