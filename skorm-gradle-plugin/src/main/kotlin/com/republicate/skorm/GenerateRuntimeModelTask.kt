package com.republicate.skorm

import com.republicate.kddl.Utils
import com.republicate.kddl.Utils.getFile
import com.republicate.skorm.SkormException
import com.republicate.skorm.core.AttributeDefinition
import com.republicate.skorm.model.RMCompositeType
import com.republicate.skorm.model.RMDatabase
import com.republicate.skorm.model.RMField
import com.republicate.skorm.model.RMItem
import com.republicate.skorm.model.RMSchema
import com.republicate.skorm.model.RMSimpleType
import org.antlr.v4.kotlinruntime.ANTLRErrorListener
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ConsoleErrorListener
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import com.republicate.skorm.parser.ksqlLexer
import com.republicate.skorm.parser.ksqlParser
import org.apache.velocity.VelocityContext
import org.gradle.api.tasks.Internal

abstract class GenerateRuntimeModelTask: BaseStructureGenerationTask() {

    init {
        description = "Skorm code generation of runtime model objects and attributes"
        group = "code generation"
    }

    private val templatePath = "templates/skorm-model.vtl"
    private val templateCorePath = "templates/skorm-model-core.vtl"
    private val templateClientPath = "templates/skorm-model-client.vtl"

    @get:InputFile
    @get:Option(option = "runtimeModel", description = "Runtime model objects and attributes")
    abstract val runtimeModel: RegularFileProperty

    @get:Option(option = "destCoreFile", description = "Destination file for core")
    @get:OutputFile
    abstract val destCoreFile: RegularFileProperty

    @get:Option(option = "destClientFile", description = "Destination file for client")
    @get:OutputFile
    abstract val destClientFile: RegularFileProperty

    @get:Internal
    protected val model: RMDatabase by lazy {
        val ksqlFilePath = project.file(runtimeModel.get()).absolutePath
        val ksql = Utils.getFile(ksqlFilePath)
        parse(ksql)
    }

    // CB TODO - we want another error listener!
    private fun parse(ksql: CharStream, errorListener: ANTLRErrorListener = ConsoleErrorListener()): RMDatabase {
        val lexer = ksqlLexer(ksql)
        val tokenStream = CommonTokenStream(lexer)
        val parser = ksqlParser(tokenStream)
        parser.addErrorListener(errorListener)
        // parser.addParseListener(tracer)
        val root = parser.database()
        return digestAST(root)
    }

    private fun digestAST(databaseContext: ksqlParser.DatabaseContext): RMDatabase {
        val database = RMDatabase(databaseContext.name?.text ?: nullerr())
        for (schemaContext in databaseContext.findSchema()) {
            val schema = RMSchema(schemaContext.name?.text ?: nullerr())
            database.schemas.add(schema)
            for (itemContext in schemaContext.findItem()) {
                val name = itemContext.name?.text ?: nullerr()
                val item = RMItem(name)
                schema.items.add(item)
                item.receiver = itemContext.receiver?.text
                item.arguments = itemContext.findArguments()?.findArgument()?.map {
                    Pair(it.LABEL()?.text!!, it.findSimple_type()?.text ?: "Any?")
                }?.toSet() ?: setOf()
                // itemContext.findArguments()?.LABEL()?.map { it.text }?.toSet()
                item.action = itemContext.attr_type!!.text!!.startsWith("mut")
                item.transaction = item.action && itemContext.findSql_spec()!!.queries != null
                val type = itemContext.findType()
                when {
                    type?.findSimple_type() != null -> item.type = RMSimpleType(type?.findSimple_type()?.text ?: nullerr(), false)
                    type?.findOut_entity() != null -> item.type = RMSimpleType(type?.findOut_entity()?.text ?: nullerr(), true)
                    type?.findComplex_type() != null -> {
                        val composite = type?.findComplex_type()?.findComplex_type_spec() ?: nullerr()
                        item.type = RMCompositeType(name.capitalize()).also { itemType ->
                            itemType.parent = composite.entity?.text
                            var fieldNames = composite.LABEL()
                            if (itemType.parent != null) fieldNames = fieldNames.subList(1, fieldNames.size)
                            val fieldTypes = composite.findSimple_type()
                            fieldNames.zip(fieldTypes).map { RMField(it.first.text, it.second.text) }.toCollection(itemType.fields)
                        }
                    }
                }
                val qualif = itemContext.findQualifier()
                when {
                    qualif == null -> {}
                    qualif.QM() != null -> item.nullable = true
                    qualif.ST() != null -> item.multiple = true
                }
                item.sql = itemContext.findSql_spec()?.query?.text?.trim() ?: itemContext.findSql_spec()?.queries?.text?.trim() ?: nullerr()
            }
        }
        return database
    }

    fun nullerr(): Nothing {
        throw Error("unexpected null value")
    }

    override fun populateContext(context: VelocityContext) {
        context.put("database", database)
        context.put("model", model)
    }

    private fun populateStructureInfos() {
        for (schema in model.schemas) {
            val dbSchema = database.schemas[schema.name] ?: throw SkormException("schema not found: ${schema.name}")
            for (item in schema.items) {
                val def = AttributeDefinition.parse(item.sql!!)
                item.parameters.addAll(def.parameters())
                val externalParameters = mutableSetOf<String>()
                externalParameters.addAll(def.parameters())
                if (item.receiver != null) {
                    val dbTable = dbSchema.tables[item.receiver!!.lowercase()] ?: throw SkormException("table not found: ${item.receiver!!.lowercase()}")
                    externalParameters.removeAll(dbTable.fields.keys)
                }
                val declaredArguments = item.arguments?.map {
                    it.first
                }?.toSet() ?: setOf()
                if (declaredArguments != externalParameters)throw SkormException(
                        "attribute parameters mismatch: expected: [${
                            declaredArguments.joinToString(",")
                        }], found: [${
                            externalParameters.joinToString(",")
                        }]"
                    )
            }
        }
    }

    @TaskAction
    fun generateObjectsCode() {
        populateStructureInfos()

        generateCode(templatePath, destFile)
        generateCode(templateCorePath, destCoreFile)
        generateCode(templateClientPath, destClientFile)
    }

//    @Internal
//    val tracer = object: ParseTreeListener {
//        override fun enterEveryRule(ctx: ParserRuleContext) {
//            logger.lifecycle("entering rule ${ctx.ruleContext.ruleIndex}")
//        }
//
//        override fun exitEveryRule(ctx: ParserRuleContext) {
//            logger.lifecycle("exiting rule ${ctx.ruleContext.ruleIndex}")
//        }
//
//        override fun visitErrorNode(node: ErrorNode) {
//            logger.lifecycle("visit error ${node.symbol.toString()}")
//        }
//
//        override fun visitTerminal(node: TerminalNode) {
//            logger.lifecycle("visit terminal ${node.symbol.toString()}")
//        }
//    }

}
