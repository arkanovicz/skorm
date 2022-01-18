package com.republicate.skorm

/*
 * Attributes
 */

// CB TODO - forbidden only on entity
val reserveAttributeNames = setOf("insert", "fetch", "update", "delete")

sealed class Attribute<out T>(val name: String) {
    private val paramNames = mutableListOf<String>()
    private val uniqueParamNames: Set<String> by lazy {
        paramNames.toSet()
    }

    fun addParameter(name: String)  = paramNames.add(name)

    /**
     * Match attribute named parameters with values found in provided context parameters
     */
    internal fun matchParamValues(vararg rawValues: Any?) : Map<String, Any?> {
        val ret = mutableMapOf<String, Any?>()
        val consumedParam = BitSet(rawValues.size)

        uniqueParamNames.forEach { p ->
            params@ for (i in rawValues.indices) {
                val value = rawValues[i]
                when(value) {
                    is Instance -> {
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
//                            ret[p] = value.entity.fields[p]?.write(found) ?: found
                            ret[p] = found
                            break@params
                        }
                    }
                    is Map<*, *> -> {
                        val found = value[p]
                        if (found != null || value.containsKey(p)) {
                            ret[p] = found
                            break@params
                        }
                    }
                    is GeneratedKeyMarker -> {
//                        ret[value.colName] = ??
                    }
                    else -> if (!consumedParam[i]) {
                        ret[p] = value
                        consumedParam.set(i, true)
                        break@params
                    }
                }
                if (i + 1 == uniqueParamNames.size) {
                    throw SQLException("attribute $name: parameter $p not found")
                }
            }
        }
        // TODO control that all simple parameters have been consumed
        // rawValues.filter { it !is Map<*, *> }.size > consumedParam.setBitsCount => error
        return ret
    }

    internal abstract fun execute(): T
}

class ScalarAttribute(name: String): Attribute<Any?>(name) {
    override fun execute(): Any? {
        TODO("Not yet implemented")
    }
}

class RowAttribute(name: String, val resultEntity: Entity? = null): Attribute<Instance?>(name) {
    override fun execute(): Instance? {
        TODO("Not yet implemented")
    }
}

class RowSetAttribute(name: String, val resultEntity: Entity? = null): Attribute<Sequence<Instance>>(name) {
    override fun execute(): Sequence<Instance> {
        TODO("Not yet implemented")
    }
}

class MutationAttribute(name: String): Attribute<Int>(name) {
    override fun execute(): Int {
        TODO("Not yet implemented")
    }
}

class TransactionAttribute(name: String): Attribute<List<Int>>(name) {
    override fun execute(): List<Int> {
        TODO("Not yet implemented")
    }
}

/*
 * Concrete attributes evaluation

class AttributeProcessor(val attributesHolder: AttributeHolder) : ExposesAttributes, ChildProcessor(attributesHolder.getProcessor()) {
    val name get() = attributesHolder.name
    private fun getAttribute(attributeName: String): Attribute<*> = attributesHolder.getAttribute(attributeName)
    override suspend fun eval(attrName: String, vararg params: Any?): Any? {
        val attr = getAttribute(attrName) as ScalarAttribute
        val paramValues = attr.matchParamValues(*params)
        val nextParam = 0

        return null
    }

}
*/
/*
 * Attributes holder
 */
abstract class AttributeHolder(val name: String, val parent: AttributeHolder? = null) {
    abstract val processor: Processor
//    protected open val entity: Entity? = null
    private val _attrMap = mutableMapOf<String, Attribute<*>>()
    private val attrMap: Map<String, Attribute<*>> get() = _attrMap
    private val path: String by lazy { (parent?.path ?: "") + "/$name" }
    private inline fun <reified T: Attribute<*>> getAttribute(attrName: String): Pair<String, T>? {
        val attr = attrMap[attrName]
        when (attr) {
            is T -> return "$path/$attrName" to attr
            null -> return null
            else -> throw SQLException("attribute $path.$attrName is not a ${T::class::simpleName}")
        }
    }
    private inline fun <reified T: Attribute<*>> findAttribute(attrName: String): Pair<String, T> {
        var holder = this
        while (true) {
            val pair = holder.getAttribute<T>(attrName)
            if (pair != null) return pair
            holder = parent ?: throw SQLException("attribute not found: $path.$attrName")
        }
    }

    fun addAttribute(attrName: String, attr: Attribute<*>) {
        val previous = _attrMap.put(attrName, attr)
        if (previous != null) throw SQLException("attribute $path.$attrName overwritten")
    }

    suspend fun eval(attrName: String, vararg params: Any?): Any? {
        val (attrPath, attr) = findAttribute<ScalarAttribute>(attrName)
        return processor.eval(attrPath, attr.matchParamValues(*params))
    }

    suspend fun retrieve(attrName: String, vararg params: Any?): Instance? {
        val (attrPath, attr) = findAttribute<RowAttribute>(attrName)
        return processor.retrieve(attrPath, attr.matchParamValues(*params), attr.resultEntity)
    }

    suspend fun query(attrName: String, vararg params: Any?): Sequence<Instance> {
        val (attrPath, attr) = findAttribute<RowSetAttribute>(attrName)
        return processor.query(attrPath, attr.matchParamValues(*params), attr.resultEntity)
    }

    suspend fun perform(attrName: String, vararg params: Any?): Long {
        val (attrPath, attr) = findAttribute<MutationAttribute>(attrName)
        return processor.perform(attrPath, attr.matchParamValues(*params))
    }

    suspend fun attempt(attrName: String, vararg params: Any?): List<Int> {
        val (attrPath, attr) = findAttribute<TransactionAttribute>(attrName)
        return processor.attempt(attrPath, attr.matchParamValues(*params))
    }
}
