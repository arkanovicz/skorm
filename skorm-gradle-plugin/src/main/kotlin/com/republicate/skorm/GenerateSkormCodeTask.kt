package com.republicate.skorm

import com.republicate.kddl.Formatter
import com.republicate.kddl.Utils
import com.republicate.kddl.hypersql.HyperSQLFormatter
import com.republicate.kddl.postgresql.PostgreSQLFormatter
import com.republicate.skorm.core.AttributeDefinition
import com.republicate.skorm.model.RMDatabase
import com.republicate.skorm.resolve.ResolvedModel
import com.republicate.skorm.resolve.Resolver
import org.apache.velocity.VelocityContext
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import java.io.File

/**
 * Single entry point for skorm code generation. What it emits depends on the platforms
 * the project builds for, unless the build declares [core] / [client] explicitly.
 *
 *   <out>/common/kotlin    entity classes, field interfaces, join and attribute accessors
 *   <out>/core/kotlin      server-side attribute registrations
 *   <out>/core/resources   database creation script
 *   <out>/client/kotlin    REST client attribute registrations
 */
abstract class GenerateSkormCodeTask : BaseModelGenerationTask() {

    init {
        description = "Skorm code generation"
        group = "code generation"
    }

    @get:InputFile
    @get:Optional
    abstract val attributes: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val dialect: Property<String>

    @get:Input
    @get:Optional
    abstract val core: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val client: Property<Boolean>

    /** Kotlin platform types the project builds for, as detected by the plugin. */
    @get:Input
    abstract val platforms: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    protected val attributeModel: RMDatabase? by lazy {
        attributes.orNull?.let { parseRuntimeModel(Utils.getFile(it.asFile.absolutePath)) }
    }

    @get:Internal
    protected lateinit var resolved: ResolvedModel

    override fun populateContext(context: VelocityContext) {
        super.populateContext(context)
        context.put("resolved", resolved)
    }

    private fun generateCore() = core.orNull ?: platforms.get().any { it in JVM_PLATFORMS }

    private fun generateClient() = client.orNull ?: platforms.get().any { it in CLIENT_PLATFORMS }

    @TaskAction
    fun generate() {
        val out = outputDirectory.get().asFile
        // a file that stops being emitted (a dropped table, a flag flipped) must not linger and get compiled
        out.deleteRecursively()
        if (attributeModel != null) checkAttributeParameters()
        resolved = Resolver(KotlinTool()).resolve(database, attributeModel)
        val withCore = generateCore()
        val withClient = generateClient()
        logger.lifecycle("$tag generating into $out (core: $withCore, client: $withClient)")

        generateCode("templates/skorm-objects.vtl", File(out, "common/kotlin/skormObjects.kt"))
        generateCode("templates/skorm-joins.vtl", File(out, "common/kotlin/skormJoins.kt"))
        if (withCore) generateCode("templates/skorm-joins-core.vtl", File(out, "core/kotlin/skormJoinsCore.kt"))
        if (withClient) generateCode("templates/skorm-joins-client.vtl", File(out, "client/kotlin/skormJoinsClient.kt"))

        if (attributeModel != null) {
            generateCode("templates/skorm-model.vtl", File(out, "common/kotlin/skormModel.kt"))
            if (withCore) generateCode("templates/skorm-model-core.vtl", File(out, "core/kotlin/skormModelCore.kt"))
            if (withClient) generateCode("templates/skorm-model-client.vtl", File(out, "client/kotlin/skormModelClient.kt"))
        }

        if (withCore) generateCreationScript(File(out, "core/resources/$CREATION_SCRIPT_FILE"))
    }

    /** An attribute's declared arguments must match the parameters its SQL actually needs. */
    private fun checkAttributeParameters() {
        for (schema in attributeModel!!.schemas) {
            val dbSchema = database.schemas[schema.name] ?: throw SkormException("schema not found: ${schema.name}")
            for (item in schema.items) {
                val def = AttributeDefinition.parse(item.sql!!)
                item.parameters.addAll(def.parameters())
                val externalParameters = def.parameters().toMutableSet()
                item.receiver?.let {
                    val table = dbSchema.tables[it.lowercase()] ?: throw SkormException("table not found: ${it.lowercase()}")
                    externalParameters.removeAll(table.fields.keys)
                }
                val declaredArguments = item.arguments?.map { it.first }?.toSet() ?: setOf()
                if (declaredArguments != externalParameters) throw SkormException(
                    "attribute parameters mismatch: expected: [${declaredArguments.joinToString(",")}]," +
                        " found: [${externalParameters.joinToString(",")}]"
                )
            }
        }
    }

    private fun generateCreationScript(destFile: File) {
        val formatter: Formatter = when (val d = dialect.orNull?.lowercase()) {
            "postgresql" -> PostgreSQLFormatter(quoted = false, uppercase = false)
            "hypersql" -> HyperSQLFormatter(quoted = false, uppercase = false)
            null -> throw GradleException(
                "skorm: no SQL dialect configured. Set `skorm { dialect = \"...\" }` to one of: postgresql, hypersql")
            else -> throw GradleException(
                "skorm: unknown SQL dialect '$d'. Valid dialects: postgresql, hypersql")
        }
        destFile.parentFile.mkdirs()
        destFile.writeText(formatter.format(database))
    }

    companion object {
        val JVM_PLATFORMS = setOf("jvm", "androidJvm")
        val CLIENT_PLATFORMS = setOf("js", "wasm", "native")
        const val CREATION_SCRIPT_FILE = "create-script.sql"
    }
}
