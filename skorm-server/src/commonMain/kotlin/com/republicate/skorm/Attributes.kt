package com.republicate.skorm

/*
 * Attributes
 */

sealed class Attribute<out T>(val name: String) {
    private val paramNames = mutableListOf<String>()
    internal val query : String by lazy { queryBuilder.toString() }

    // building
    // No, this code is only server side!
    private val queryBuilder = StringBuilder()
    fun addParameter(name: String) {
        paramNames.add(name)
        queryBuilder.append("?")
    }

    /**
     * Match attribute named parameters with values found in provided context parameters
     */
    internal fun matchParamValues(vararg rawValues: Any?) : Array<Any?> {
        val ret = arrayOfNulls<Any?>(paramNames.size)
        paramNames.forEachIndexed { i, p ->

        }
        return ret
    }

    protected abstract fun execute(): T
}

class ScalarAttribute(name: String): Attribute<Any?>(name) {
    override fun execute(): Any? {
        TODO("Not yet implemented")
    }
}

class SimpleAttribute(name: String): Attribute<Instance?>(name) {
    override fun execute(): Instance {
        TODO("Not yet implemented")
    }
}

class MultiAttribute(name: String): Attribute<Sequence<Instance>>(name) {
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
 * Attributes calling API
 */

interface WithCallableAttributes {
    fun eval(attributeName: String, vararg params: Any?): Any?
    fun retrieve(attributeName: String, vararg params: Any?): Instance?
    fun query(attributeName: String, vararg params: Any?): Sequence<Instance>
    fun perform(attributeName: String, vararg params: Any?): Int
    fun attempt(attributeName: String, vararg params: Any?): List<Int>
}

/*
 * Concrete attributes evaluation
 */

class AttributeProcessor(val attributesHolder: AttributeHolder) : ExposesAttributes {
    val name get() = attributesHolder.name
    private fun getAttribute(attributeName: String): Attribute<*> = attributesHolder.getAttribute(attributeName)
    override fun eval(attrName: String, vararg params: Any?): Any? {
        val attr = getAttribute(attrName) as ScalarAttribute
        val paramValues = attr.matchParamValues(*params)
        val nextParam = 0

        return null
    }

    override fun retrieve(attrName: String, vararg params: Any?): Instance? {
        val attr =  getAttribute(attrName) as RowAttribute
        return null
    }

    override fun query(attrName: String, vararg params: Any?): Sequence<Instance> {
        val attr =  getAttribute(attrName) as RowsetAttribute
        return sequenceOf()
    }

    override fun perform(attrName: String, vararg params: Any?): Int {
        val attr =  getAttribute(attrName) as MutationAttribute
        return 0
    }

    override fun attempt(attrName: String, vararg params: Any?): List<Int> {
        val attr =  getAttribute(attrName) as TransactionAttribute
        return emptyList()
    }
}

/*
 * Attributes holder
 */


open class AttributeHolder(val name: String, val parent: AttributeHolder? = null) {
    private val attrMap: Map<String, Attribute<*>> = emptyMap()
    fun getAttribute(attrName: String): Attribute<*> = attrMap.getOrElseNullable(attrName) {
        parent?.getAttribute(attrName) ?: throw SQLException("attribute $name.$attrName not found")
    }
}
