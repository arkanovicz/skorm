package com.republicate.skorm

import com.republicate.skorm.Query.Companion.ParserState.*

data class Query(val stmt: String, val params: List<String>) {
    companion object {
        // CB TODO - this is driver specific, but we try to have a kinda generic handling
        val lexer = Regex("\\{|\\}|\\bbegin\\b|\\bend\\b|'|\\\"|\\[|\\]|;", RegexOption.IGNORE_CASE)
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
        val stateMap = ParserState.values().associateBy { it.start }.toMap()
        fun parse(qry: String): List<Query> {
            val trimmed = qry.trim()
            val raw = if (trimmed.endsWith((';'))) trimmed else "$trimmed;"
            val ret = mutableListOf<Query>()
            val states = mutableListOf(INITIAL)
            fun state() = states.last()
            fun push(state: ParserState) = states.add(state)
            fun pop() = states.removeLast()
            var pos = 0
            val queryPart = StringBuilder()
            val params = mutableListOf<String>()
            lexer.findAll(raw).forEach { match ->
                val before = qry.substring(pos, match.range.first)
                if (states.last() == PARAMETER) {
                    if (match.value != PARAMETER.end) throw SkormException("invalid parameter name")
                    params.add(before.trim().also {
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
                        queryPart.append(match.value)
                        if (state() == INITIAL) {
                            val nextState = stateMap[match.value.lowercase()] ?: throw SkormException("unhandled case")
                            when (nextState) {
                                BLOCK, QUOTED, DOUBLE_QUOTED, BRACKETED -> push(nextState)
                                ERROR -> throw SkormException("provided queries should not have '?' markers")
                                END -> {
                                    ret.add(Query(queryPart.toString(), params))
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
            return ret
        }
    }
}

sealed interface QueryDef {
    fun queries(params: Collection<String>): List<Query>
}

class SimpleQuery(val query: Query): QueryDef {
    override fun queries(params: Collection<String>) = listOf(query)
}

class MultipleQuery(val queries: List<Query>): QueryDef {
    override fun queries(params: Collection<String>) = queries
}

class DynamicQuery(val generator: (Collection<String>)-> Query): QueryDef {
    private val queryCache = concurrentMapOf<String, Query>()
    override fun queries(params: Collection<String>): List<Query> {
        val key = params.sorted().joinToString("#")
        return listOf(queryCache.getOrPut(key) {
            generator(params)
        })
    }
}
