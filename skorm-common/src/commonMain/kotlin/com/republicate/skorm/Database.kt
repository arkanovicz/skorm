package com.republicate.skorm

import com.republicate.kson.Json

open class Database protected constructor(name: String, override val processor: Processor): AttributeHolder(name), Configurable {
    var initialized by initOnce(false)
    override val config = Configuration()
    override fun configure(cfg: Map<String, Any?>) {
        if (initialized) throw SkormException("Already initialized")
        super.configure(cfg)
    }

    private val _schemas = mutableMapOf<String, Schema>()
    val schemas: Collection<Schema> get() = _schemas.values
    fun schema(name: String) = _schemas[name] ?: throw SkormException("no such schema: $name")
    internal fun addSchema(schema: Schema) {
        _schemas[schema.name] = schema
    }

    override fun initialize() {
        if (initialized) throw RuntimeException("Already initialized")
        processor.initialize(processor.configTag?.let { config.getObject(it) })
        initialized = true // CB TODO - it really means *populated* (via reverse engineering or structure file)
        for (entity in schemas.flatMap { it.entities }) {
            processor.register(entity)
        }
    }
}

open class Schema protected constructor(name: String, parent: Database) : AttributeHolder(name, parent) {
    init {
        @Suppress("LeakingThis")
        parent.addSchema(this)
    }

    val database: Database get() = parent as Database
    override val processor get() = database.processor

    private val _entities = mutableMapOf<String, Entity>()
    val entities: Collection<Entity> get() = _entities.values
    fun entity(name: String) = _entities[name] ?: throw SkormException("no such entity: $name")
    fun addEntity(entity: Entity) {
        if (database.initialized) throw RuntimeException("Already initialized")
        _entities[entity.name] = entity
    }
}

open class Entity protected constructor(val name: String, schema: Schema) {
    init {
        @Suppress("LeakingThis")
        schema.addEntity(this)
    }

    /*private*/ val instanceAttributes = object : AttributeHolder(name, schema) {
        override val processor get() = schema.processor
    }
    val schema get() = instanceAttributes.parent as Schema
    val path get() = instanceAttributes.path

    private val _fields = mutableMapOf<String, Field>()
    val fields: Map<String, Field> get() = _fields
    val fieldNames: List<String> by lazy {
        _fields.map { it.key }
    }
    val fieldIndices: Map<String, Int> by lazy {
        _fields.entries.mapIndexed { index, entry -> entry.key to index }.toMap()
    }
    fun addField(field: Field) {
        if (schema.database.initialized) throw RuntimeException("Already initialized")
        _fields[field.name] = field
    }

    val primaryKey: List<Field> by lazy { _fields.values.filter { it.primary } }

    private val fetchAttribute: InstanceAttribute by lazy {
        InstanceAttribute(instanceAttributes, "fetch", primaryKey.map { it.name }.toSet(), this).apply {
            check(schema.database.initialized)
        }
    }

    private val browseAttribute: BagAttribute by lazy {
        BagAttribute(instanceAttributes, "browse", emptySet(), this).apply {
            check(schema.database.initialized)
        }
    }

    private val insertAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "insert", useDirtyFields = true).apply {
            check(schema.database.initialized)
        }
    }

    private val updateAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "update", useDirtyFields = true).apply {
            check(schema.database.initialized)
        }
    }

    private val deleteAttribute: MutationAttribute by lazy {
        MutationAttribute(instanceAttributes, "delete", parameters = primaryKey.map { it.name }.toSet()).apply {
            check(schema.database.initialized)
        }
    }

    open fun new() = Instance(this)

    open suspend fun fetch(vararg key: Any): Instance? = fetchAttribute.execute(*key)
    open suspend fun browse() = browseAttribute.execute()
    open suspend operator fun iterator() = browse().iterator()

    // Other operations are not visible directly, they are proxied from Instance
    internal suspend fun insert(instance: Instance): Long {
        return if (primaryKey.size == 1 && primaryKey.first().generated) {
            insertAttribute.execute(instance, GeneratedKeyMarker(primaryKey.first().name))
        } else {
            insertAttribute.execute(instance)
        }
    }
    internal suspend fun update(instance: Instance) = updateAttribute.execute(instance)
    internal suspend fun delete(instance: Instance) = deleteAttribute.execute(instance)
    /*internal*/ suspend inline fun <reified T: Any?> eval(attrName: String, vararg params: Any?) = instanceAttributes.eval<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) = instanceAttributes.retrieve<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) = instanceAttributes.query<T>(attrName, *params)
    internal suspend fun perform(attrName: String, vararg params: Any?) = instanceAttributes.perform(attrName, *params)
}

open class Instance(val entity: Entity) : Json.MutableObject() {
    val dirtyFields = BitSet(MAX_FIELDS) // init size needed for multiplatform
    val generatedPrimaryKey: Boolean get() = entity.primaryKey.size == 1 && entity.primaryKey.first().generated
    var persisted = false

    // instance mutations

    suspend fun insert() {
        if (persisted) throw SkormException("cannot insert a persisted instance")
        if (generatedPrimaryKey && containsKey(entity.primaryKey.first().name)) throw SkormException("generated primary key value cannot be specified at insertion")
        val ret = entity.insert(this)
        if (generatedPrimaryKey) put(entity.primaryKey.first().name, ret)
        else if (ret != 1L) throw SkormException("unexpected number of changed rows, expected 1, found $ret")
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

    suspend fun refresh() {
        if (!persisted) throw SkormException("cannot refresh a volatile instance")
        val self = entity.fetch(this) ?: throw SkormException("cannot refresh instance, it doesn't exist")
        putAll(self)
    }

    // dirty flags handling

    fun setClean() {
        dirtyFields.clear()
    }

    fun isDirty() = dirtyFields.nextSetBit(0) != -1

    override fun putAll(from: Map<out String, Any?>) {
        from.entries.filter { entity.fields.contains(it.key) }.forEach { put(it.key, it.value) }
    }

    override fun put(key: String, value: Any?): Any? {
        val ret = super.put(key, value)
        val field = entity.fields[key] ?: throw SkormException("${entity.name} has no field named $key")
        if (persisted && field.primary && ret != value) // CB TODO - since 'value' type is lax, 'value' may need a proper conversion before the comparison
                persisted = false
        dirtyFields.set(entity.fieldIndices[key]!!, true)
        return ret
    }


    suspend inline fun <reified T: Any?> eval(attrName: String, vararg params: Any?) = entity.eval<T>(attrName, this, *params)
    suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) = entity.retrieve<T>(attrName, entity, this, *params)
    suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) = entity.query<T>(attrName, entity, this, *params)
    suspend fun perform(attrName: String, vararg params: Any?) = entity.perform(attrName, this, *params)

    internal fun dirtyFieldNames(): Iterator<String> = object: Iterator<String> {
        var index = dirtyFields.nextSetBit(0)

        override operator fun hasNext() = (index != -1)
        override operator fun next(): String {
            return entity.fieldNames[index].also {
                index = dirtyFields.nextSetBit(index + 1)
            }
        }
    }
}
