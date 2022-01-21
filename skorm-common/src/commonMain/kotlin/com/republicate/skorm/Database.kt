package com.republicate.skorm

import com.republicate.kson.Json

open class Database(name: String): AttributeHolder(name) {
    var configured by initOnce(false)
    var populated by initOnce(false)

    override lateinit var processor: Processor

    private val _schemas = mutableMapOf<String, Schema>()
    val schemas: Map<String, Schema> get() = _schemas
    internal fun addSchema(schema: Schema) {
        _schemas[schema.name] = schema
    }
}

open class Schema(name: String, parent: Database) : AttributeHolder(name, parent) {
    init { parent.addSchema(this) }

    val database: Database get() = parent as Database
    override val processor get() = database.processor

    private val _entities = mutableMapOf<String, Entity>()
    val entities: Map<String, Entity> by _entities
    fun addEntity(entity: Entity) {
        _entities[entity.name] = entity
    }
}

open class Entity(val name: String, schema: Schema) {
    init {
        schema.addEntity(this)
    }

    protected val instanceAttributes = object : AttributeHolder(name, schema) {
        override val processor get() = schema.processor
    }
    val schema get() = instanceAttributes.parent as Schema

    private val _fields = mutableMapOf<String, Field>()
    val fields: Map<String, Field> get() = _fields
    fun addField(field: Field) {
        check(!schema.database.populated)
        _fields[field.name] = field
    }

    val primaryKey: List<Field> by lazy { _fields.values.filter { it.primary } }

    private val fetchAttribute: RowAttribute by lazy {
        RowAttribute(instanceAttributes, "fetch", this).apply {
            check(schema.database.populated)
            primaryKey.forEach {
                addParameter(it.name)
            }
        }
    }

    private val browseAttribute: RowSetAttribute by lazy {
        RowSetAttribute(instanceAttributes, "browse", this).apply {
            check(schema.database.populated)
        }
    }

    private val insertAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "insert").apply {
            check(schema.database.populated)
            fields.values.filter { !it.generated }.forEach {
                addParameter(it.name)
            }
        }
    }

    private val updateAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "update").apply {
            check(schema.database.populated)
            fields.values.filter { !it.primary }.forEach {
                addParameter(it.name)
            }
        }
    }

    private val deleteAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "delete").apply {
            check(schema.database.populated)
            primaryKey.forEach {
                addParameter(it.name)
            }
        }
    }

    open fun new() = Instance(this)

    open suspend fun fetch(vararg key: Any): Instance? = fetchAttribute.execute(*key)
    open suspend operator fun iterator() = browseAttribute.execute().iterator()

    // Other operations are not visible directly, they are proxied from Instance
    internal suspend fun insert(instance: Instance) = insertAttribute.execute(instance)
    internal suspend fun update(instance: Instance) = updateAttribute.execute(instance)
    internal suspend fun delete(instance: Instance) = deleteAttribute.execute(instance)
    internal suspend fun eval(attrName: String, vararg params: Any?) = instanceAttributes.eval(attrName, *params)
    internal suspend fun retrieve(attrName: String, vararg params: Any?) = instanceAttributes.retrieve(attrName, *params)
    internal suspend fun query(attrName: String, vararg params: Any?) = instanceAttributes.query(attrName, *params)
    internal suspend fun perform(attrName: String, vararg params: Any?) = instanceAttributes.perform(attrName, *params)
}

open class Instance(val entity: Entity) : Json.Object() {
    val dirtyFields = BitSet(MAX_FIELDS) // init size needed for multiplatform
    var persisted = false

    // instance mutations

    suspend fun insert() {
        if (persisted) throw SkormException("cannot insert a persisted instance")

        entity.insert(this)
        persisted = true
        setClean()
    }

    suspend fun update() {
        if (!persisted) throw SkormException("cannot update a volatile instance")
        entity.update(this)
        setClean()
    }

    suspend fun upsert() = if (persisted) update() else delete()

    suspend fun delete() {
        if (!persisted) throw SkormException("cannot delete a volatile instance")
        entity.delete(this)
        persisted = false
    }

    // dirty flags handling
    private fun setClean() {
        dirtyFields
    }

    suspend fun eval(attrName: String, vararg params: Any?) = entity.eval(attrName, this, *params)
    suspend fun retrieve(attrName: String, vararg params: Any?) = entity.retrieve(attrName, entity, this, *params)
    suspend fun query(attrName: String, vararg params: Any?) = entity.query(attrName, entity, this, *params)
    suspend fun perform(attrName: String, vararg params: Any?) = entity.perform(attrName, this, *params)
}
