package com.republicate.skorm

import com.republicate.skorm.model.RMCompositeType
import com.republicate.skorm.model.RMDatabase
import com.republicate.skorm.model.RMField
import com.republicate.skorm.model.RMItem
import com.republicate.skorm.model.RMSchema
import com.republicate.skorm.model.RMSimpleType
import com.republicate.skorm.parser.ksqlLexer
import com.republicate.skorm.parser.ksqlParser
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer

/** Parses [ksql], failing on the first syntax error: a model must not generate from a recovered, truncated tree. */
fun parseRuntimeModel(ksql: CharStream, source: String = "ksql"): RMDatabase {
    val strict = object : BaseErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            throw SkormException("$source: line $line:$charPositionInLine $msg")
        }
    }
    val lexer = ksqlLexer(ksql).apply { removeErrorListeners(); addErrorListener(strict) }
    val tokenStream = CommonTokenStream(lexer)
    val parser = ksqlParser(tokenStream).apply { removeErrorListeners(); addErrorListener(strict) }
    val root = parser.database()
    return digestAST(root)
}

private fun digestAST(databaseContext: ksqlParser.DatabaseContext): RMDatabase {
    val database = RMDatabase(databaseContext.name?.text ?: nullerr())
    for (schemaContext in databaseContext.schema()) {
        val schema = RMSchema(schemaContext.name?.text ?: nullerr())
        database.schemas.add(schema)
        for (itemContext in schemaContext.item()) {
            val name = itemContext.name?.text ?: nullerr()
            val item = RMItem(name)
            schema.items.add(item)
            item.receiver = itemContext.receiver?.text
            item.arguments = itemContext.arguments()?.argument()?.map {
                Pair(it.name!!.text!!, it.simple_type()?.text ?: it.enumType?.text ?: "Any?")
            }?.toSet() ?: setOf()
            // itemContext.findArguments()?.LABEL()?.map { it.text }?.toSet()
            item.action = itemContext.attr_type!!.text!!.startsWith("mut")
            item.transaction = item.action && itemContext.sql_spec().queries != null
            val type = itemContext.type()
            when {
                type?.simple_type() != null -> item.type = RMSimpleType(type.simple_type()?.text ?: nullerr(), false)
                type?.json_object_type() != null -> item.type = RMSimpleType(type.json_object_type()?.text ?: nullerr(), false)
                type?.out_entity() != null -> item.type = RMSimpleType(type.out_entity()?.text ?: nullerr(), true)
                type?.complex_type() != null -> {
                    val composite = type.complex_type()?.complex_type_spec() ?: nullerr()
                    item.type = RMCompositeType(name.withCapital()).also { itemType ->
                        itemType.parent = composite.entity?.text
                        var fieldNames = composite.LABEL()
                        if (itemType.parent != null) fieldNames = fieldNames.subList(1, fieldNames.size)
                        val fieldTypes = composite.simple_type()
                        fieldNames.zip(fieldTypes).map { RMField(it.first.text, it.second.text) }.toCollection(itemType.fields)
                    }
                }
            }
            val qualif = itemContext.qualifier()
            when {
                qualif == null -> {}
                qualif.QM() != null -> item.nullable = true
                qualif.ST() != null -> item.multiple = true
            }
            // a block's statements, without its braces: to the query parser `{` opens a parameter
            item.sql = itemContext.sql_spec().query?.text?.trim()
                ?: itemContext.sql_spec().queries?.sql()?.joinToString("\n") { it.text.trim() + ";" }
                ?: nullerr()
        }
    }
    return database
}

private fun nullerr(): Nothing {
    throw Error("unexpected null value")
}
