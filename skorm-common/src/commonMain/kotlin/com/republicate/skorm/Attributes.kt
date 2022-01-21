package com.republicate.skorm

/*
 * Attributes
 */

// CB TODO - forbidden only on entity
val reserveAttributeNames = setOf("insert", "fetch", "update", "delete")

sealed class Attribute<out T>(val holder: AttributeHolder, val name: String) {
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
                    throw SkormException("attribute $name: parameter $p not found")
                }
            }
        }
        // TODO control that all simple parameters have been consumed
        // rawValues.filter { it !is Map<*, *> }.size > consumedParam.setBitsCount => error
        return ret
    }

    internal abstract suspend fun execute(vararg params: Any?): T
}

class ScalarAttribute(holder: AttributeHolder, name: String):
    Attribute<Any?>(holder, name) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.eval("${holder.path}/$name", matchParamValues(*params))
}

class RowAttribute(holder: AttributeHolder, name: String, val resultEntity: Entity? = null):
    Attribute<Instance?>(holder, name) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.retrieve("${holder.path}/$name", matchParamValues(*params), resultEntity)
}

class RowSetAttribute(holder: AttributeHolder, name: String, val resultEntity: Entity? = null): Attribute<Sequence<Instance>>(holder, name) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.query("${holder.path}/$name", matchParamValues(*params), resultEntity)
}

class MutationAttribute(holder: AttributeHolder, name: String): Attribute<Long>(holder, name) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.perform("${holder.path}/$name", matchParamValues(*params))
}

class TransactionAttribute(holder: AttributeHolder, name: String): Attribute<List<Int>>(holder, name) {
    override suspend fun execute(vararg params: Any?) =
        holder.processor.attempt("${holder.path}/$name", matchParamValues(*params))
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
    private val _attrMap = mutableMapOf<String, Attribute<*>>()
    private val attrMap: Map<String, Attribute<*>> get() = _attrMap
    internal val path: String by lazy { (parent?.path ?: "") + "/$name" }

    private inline fun <reified T: Attribute<*>> getAttribute(attrName: String): T? {
        val attr = attrMap[attrName]
        when (attr) {
            is T -> return attr
            null -> return null
            else -> throw SkormException("attribute $path.$attrName is not a ${T::class::simpleName}")
        }
    }

    private inline fun <reified T> findAttribute(attrName: String): Attribute<T> {
        var holder = this
        while (true) {
            val attr = holder.getAttribute<Attribute<T>>(attrName)
            if (attr != null) return attr
            holder = holder.parent ?: throw SkormException("attribute not found: $path.$attrName")
        }
    }

    fun addAttribute(attr: Attribute<*>) {
        val previous = _attrMap.put(attr.name, attr)
        if (previous != null) throw SkormException("attribute $path.${attr.name} overwritten")
    }

    suspend fun eval(attrName: String, vararg params: Any?) =
        findAttribute<Any?>(attrName).execute(*params)

    suspend fun retrieve(attrName: String, vararg params: Any?) =
        findAttribute<Instance?>(attrName).execute(*params)

    suspend fun query(attrName: String, vararg params: Any?) =
        findAttribute<Sequence<Instance>>(attrName).execute(*params)

    suspend fun perform(attrName: String, vararg params: Any?) =
        findAttribute<Long>(attrName).execute(*params)

    suspend fun attempt(attrName: String, vararg params: Any?) =
        findAttribute<List<Int>>(attrName)
}
