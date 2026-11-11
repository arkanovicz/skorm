package com.republicate.skorm

import com.republicate.kddl.ASTDatabase
import com.republicate.kddl.ASTSchema
import com.republicate.kddl.FieldType
import com.republicate.kddl.SearchPathResolver
import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.kddl.gradle.IncludedModels
import com.republicate.kddl.parse
import com.republicate.kddl.reverse
import org.apache.velocity.VelocityContext
import org.apache.velocity.tools.generic.LogTool
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option

@CacheableTask
abstract class BaseModelGenerationTask: BaseGenerationTask() {

    init {
        outputs.doNotCacheIf("the model is reverse-engineered from a live datasource") { datasource.isPresent }
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Option(option = "model", description = "Source kddl model")
    @get:Optional
    abstract val model: RegularFileProperty

    /** Directories and jars where an `import` is looked up, after the model's own directory. */
    @get:Classpath
    abstract val imports: ConfigurableFileCollection

    /** The model files the root includes or imports: editing one of them reruns the task. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val included: FileCollection = project.files(
        project.providers.of(IncludedModels::class.java) {
            it.parameters.model.set(model)
            it.parameters.imports.from(imports)
        }
    )

    @get:Input
    @get:Option(option = "datasource", description = "Datasource JDBC URL with credentials")
    @get:Optional
    abstract val datasource: Property<String>

    // read by the action, on every run: a field set any earlier is stored with the cached configuration,
    // and the next run would generate from the model as it was then
    @get:Internal
    protected lateinit var database: ASTDatabase

    protected fun readModel(): ASTDatabase {
        val foundModel = model.orNull
        val foundDatasource = datasource.orNull
        if ((foundModel == null) == (foundDatasource == null)) {
            throw RuntimeException("$tag expecting exactly one of skorm.model or skorm.datasource parameter")
        }
        return if (foundModel != null) parse(Utils.getFile(foundModel.asFile.absolutePath), resolver = SearchPathResolver(imports.files)) else reverse(foundDatasource!!)
    }

    override fun populateContext(context: VelocityContext) {
        val usedPrimitiveBases = database.schemas.values
            .filter { schema: ASTSchema -> !schema.external }
            .flatMap { schema: ASTSchema -> schema.tables.values }
            .flatMap { table -> table.fields.values }
            .mapNotNull { field -> (field.type as? FieldType.Primitive)?.base?.lowercase() }
            .toSet()
        context.put("database", database)
        context.put("useDatetimeType", usedPrimitiveBases.any { it in setOf("timestamp", "date", "time") })
        context.put("useInstantType", "timestamptz" in usedPrimitiveBases)
        context.put("useUuidType", "uuid" in usedPrimitiveBases)
        context.put("useJsonType", "json" in usedPrimitiveBases || "jsonb" in usedPrimitiveBases)
        context.put("logger", LogTool())
    }

}
