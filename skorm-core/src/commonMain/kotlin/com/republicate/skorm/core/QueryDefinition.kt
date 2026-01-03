package com.republicate.skorm.core

import com.republicate.skorm.IdentifierMapper
import com.republicate.skorm.SkormException
// import com.republicate.skorm.concurrentMapOf
import com.republicate.skorm.core.AttributeDefinition.Companion.ParserState.*
import com.republicate.skorm.identityMapper

data class QueryDefinition(val stmt: String, val params: List<String>) {
}

sealed interface AttributeDefinition {

    companion object {
        // CB TODO - this is driver specific, but we try to have a kinda generic handling
        private val lexer = Regex("\\{|\\}|\\bbegin\\b|\\bend\\b|'|\\\"|\\[|\\]|;", RegexOption.IGNORE_CASE)
        enum class ParserState(val start: String, val end: String = start, val allowParams: Boolean = false) {
            INITIAL(start="", allowParams=true),
            PARAMETER(start="{", end="}", allowParams=false),
            BLOCK(start="begin", end="end", allowParams=true),
            QUOTED(start="'", allowParams=false),
            DOUBLE_QUOTED(start="\"", allowParams=false),
            BRACKETED(start="[", end="]", allowParams=false),
            ERROR(start="?", allowParams=false),
            END(start=";")
        }
        private val stateMap = ParserState.values().associateBy { it.start }.toMap()
        fun parse(qry: String, schema: String = "", parameterIdentifierMapper: IdentifierMapper = identityMapper): AttributeDefinition {
            val trimmed = qry.trim()
            val raw = if (trimmed.endsWith((';'))) trimmed else "$trimmed;"
            val queries = mutableListOf<QueryDefinition>()
            val states = mutableListOf(INITIAL)
            fun state() = states.last()
            fun push(state: ParserState) = states.add(state)
            fun pop() = states.removeLast()
            var pos = 0
            val queryPart = StringBuilder()
            val params = mutableListOf<String>()
            // TODO - this regexp lexer is quick and dirty parsing hack, we would need a word-by-word tokenizer.
            // For instance, BEGIN... CASE WHEN END... END will be unproperly parsed.
            lexer.findAll(raw).forEach { match ->
                val before = raw.substring(pos, match.range.first)
                if (states.last() == PARAMETER) {
                    if (match.value != PARAMETER.end) throw SkormException("invalid parameter name")
                    params.add(before.trim().let {
                        parameterIdentifierMapper(it)
                    }.also {
                        if (!it.matches(Regex("\\w+")))
                            throw SkormException("invalid parameter name")
                    })
                    queryPart.append("?")
                    pop()
                } else {
                    queryPart.append(before)
                    if (match.value.lowercase() == state().end) {
                        queryPart.append(match.value)
                        pop()
                    } else if (state().allowParams && match.value == PARAMETER.start) {
                        push(PARAMETER)
                    } else {
                        val token = match.value
                        queryPart.append(token)
                        if (state() == INITIAL && token.lowercase() != BLOCK.end) {
                            val nextState = stateMap[token.lowercase()] ?: throw SkormException("unhandled case")
                            when (nextState) {
                                BLOCK, QUOTED, DOUBLE_QUOTED, BRACKETED -> push(nextState)
                                INITIAL, PARAMETER -> throw SkormException("unexpected case")
                                ERROR -> throw SkormException("provided queries should not have '?' markers")
                                END -> {
                                    queries.add(QueryDefinition(queryPart.toString(), params.toList()))
                                    queryPart.clear()
                                    params.clear()
                                }
                            }
                        }
                    }
                }
                pos = match.range.last + 1
            }
            if (pos < trimmed.length) throw SkormException("inconsistency")
            return when (queries.size) {
                0 -> throw SkormException("could not parse statement")
                1 -> SimpleQuery(schema, queries.first())
                else -> MultipleQuery(schema, queries)
            }
        }
    }

    val schema: String

    fun queries(params: Collection<String>): List<QueryDefinition>
    fun parameters(): Set<String>

    override fun toString(): String
}

class SimpleQuery(override val schema: String, val queryDefinition: QueryDefinition): AttributeDefinition {
    override fun queries(params: Collection<String>) = listOf(queryDefinition)
    override fun parameters() = queryDefinition.params.toSet()
    override fun toString() = "(${queryDefinition.params.joinToString(", ")}) -> [${queryDefinition.stmt}]"
}

class MultipleQuery(override val schema: String, val queries: List<QueryDefinition>): AttributeDefinition {
    override fun queries(params: Collection<String>) = queries
    override fun parameters() = queries.flatMap { it.params }.toSet()
    override fun toString() = "(${parameters().joinToString(", ")}) -> [${queries.joinToString("; ") { it.stmt }}]"
}

class DynamicQuery(override val schema: String, val generator: (Collection<String>)-> QueryDefinition): AttributeDefinition {
    /* TODO ...
    private val queryCache = concurrentMapOf<String, QueryDefinition>()
    override fun queries(params: Collection<String>): List<QueryDefinition> {
        val key = params.sorted().joinToString("#")
        return listOf(queryCache.getOrPut(key) {
            generator(params)
        })
     */
    override fun queries(params: Collection<String>): List<QueryDefinition> {
        val key = params.sorted().joinToString("#")
        return listOf(generator(params))
    }

    override fun parameters(): Set<String> { throw SkormException("cannot get parameters of a dynamic query") }
    override fun toString() = "[dynamic query]"
}
