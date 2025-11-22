package com.republicate.skorm

import com.republicate.kson.Json

open class Database protected constructor(name: String, override val processor: Processor): AttributeHolder(name), Configurable, AutoCloseable {
    var populated by initOnce(false)
    override val config = Configuration()
    override fun configure(cfg: Map<String, Any?>) {
        if (populated) throw SkormException("Already initialized")
        super.configure(cfg)
    }

    private val _schemas = mutableMapOf<String, Schema>()
    val schemas: Collection<Schema> get() = _schemas.values
    fun schema(name: String) = _schemas[name] ?: throw SkormException("no such schema: $name")
    internal fun addSchema(schema: Schema) {
        _schemas[schema.name] = schema
    }

    override fun initialize() {
        if (populated) throw RuntimeException("Already initialized")
        processor.initialize(processor.configTag?.let { config.getObject(it) })
        populated = true
        for (entity in schemas.flatMap { it.entities }) {
            processor.register(entity)
        }
    }

    override fun close() {
        processor.close()
    }
}

open class Schema protected constructor(name: String, parent: Database) : AttributeHolder(name, parent) {
    init {
        @Suppress("LeakingThis")
        parent.addSchema(this)
    }

    val database: Database get() = parent as Database
    override val processor get() = database.processor
    override val schema get() = this

    private val _entities = mutableMapOf<String, Entity>()
    val entities: Collection<Entity> get() = _entities.values
    fun entity(name: String) = _entities[name] ?: throw SkormException("no such entity: $name")
    fun addEntity(entity: Entity) {
        if (database.populated) throw RuntimeException("Already initialized")
        _entities[entity.name] = entity
    }
}

open class Entity protected constructor(val name: String, val schema: Schema) {
    init {
        @Suppress("LeakingThis")
        schema.addEntity(this)
    }

    inner class InstanceAttributes: AttributeHolder(name, schema) {
        override val processor get() = schema.processor
        override val schema get() = parent as Schema

        override fun prepare(attr: Attribute<*>, vararg params: Any?): Pair<String, Map<String, Any?>> {
            val doRestPK = processor.restMode && params.isNotEmpty() && params[0] is Instance && (params[0] as Instance).isPersisted
            return if (doRestPK) {
                val instance = params[0] as Instance
                val pkFields = instance.entity.primaryKey.map { it.name }
                val execPath = "$path/${
                    pkFields.joinToString("/") {
                        instance[it].toString()
                    }
                }/${attr.name}"
                val execParams = attr.matchParamValues(*params).entries.filter {
                    !pkFields.contains(it.key)
                }.associate {
                    it.key to it.value
                }
                Pair(execPath, execParams)
            }
            else super.prepare(attr, *params)
        }
    }

    /*private*/ val instanceAttributes = InstanceAttributes()
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
        if (schema.database.populated) throw RuntimeException("Already initialized")
        _fields[field.name] = field
    }

    val primaryKey: List<Field> by lazy { _fields.values.filter { it.isPrimary } }

    private val fetchAttribute: RowAttribute<Instance> by lazy {
        RowAttribute<Instance>("fetch", primaryKey.map { it.name }.toSet(), this::new).apply {
            check(schema.database.populated)
        }
    }

    private val browseAttribute: RowSetAttribute<Instance> by lazy {
        RowSetAttribute<Instance>("browse", emptySet(), this::new).apply {
            check(schema.database.populated)
        }
    }

    private val insertAttribute: MutationAttribute by lazy {
        MutationAttribute("insert", useDirtyFields = true).apply {
            check(schema.database.populated)
        }
    }

    private val updateAttribute: MutationAttribute by lazy {
        MutationAttribute("update", useDirtyFields = true).apply {
            check(schema.database.populated)
        }
    }

    private val deleteAttribute: MutationAttribute by lazy {
        MutationAttribute("delete", parameters = primaryKey.map { it.name }.toSet()).apply {
            check(schema.database.populated)
        }
    }

    open fun new() = Instance(this)

    open suspend fun fetch(vararg key: Any): Instance? = instanceAttributes.retrieve(fetchAttribute, *key)
    open suspend fun browse() = instanceAttributes.query<Instance>(browseAttribute)
    open suspend operator fun iterator() = browse().iterator()

    // Other operations are not visible directly, they are proxied from Instance
    internal suspend fun insert(instance: Instance): Long {
        return if (primaryKey.size == 1 && primaryKey.first().isGenerated) {
            instanceAttributes.perform(insertAttribute, instance, GeneratedKeyMarker(primaryKey.first().name))
        } else {
            instanceAttributes.perform(insertAttribute, instance)
        }
    }
    internal suspend fun update(instance: Instance) = instanceAttributes.perform(updateAttribute, instance)
    internal suspend fun delete(instance: Instance) = instanceAttributes.perform(deleteAttribute, instance)
    /*internal*/ suspend inline fun <reified T: Any?> eval(attrName: String, vararg params: Any?) = instanceAttributes.eval<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) = instanceAttributes.retrieve<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) = instanceAttributes.query<T>(attrName, *params)
    internal suspend fun perform(attrName: String, vararg params: Any?) = instanceAttributes.perform(attrName, *params)
}

open class Instance(val entity: Entity) : Json.MutableObject() {
    val dirtyFields = BitSet(MAX_FIELDS) // init size needed for multiplatform
    val generatedPrimaryKey: Boolean get() = entity.primaryKey.size == 1 && entity.primaryKey.first().isGenerated
    var isPersisted = false
    private val processor get() = entity.instanceAttributes.processor

    // instance mutations

    suspend fun insert() {
        if (isPersisted) throw SkormException("cannot insert a persisted instance")
        if (generatedPrimaryKey && containsKey(entity.primaryKey.first().name)) throw SkormException("generated primary key value cannot be specified at insertion")
        val ret = entity.insert(this)
        if (generatedPrimaryKey) put(entity.primaryKey.first().name, ret)
        else if (ret != 1L) throw SkormException("unexpected number of changed rows, expected 1, found $ret")
        isPersisted = true
        setClean()
    }

    suspend fun update() {
        if (!isPersisted) throw SkormException("cannot update a volatile instance")
        entity.update(this)
        setClean()
    }

    suspend fun upsert() = if (isPersisted) update() else insert()

    suspend fun delete() {
        if (!isPersisted) throw SkormException("cannot delete a volatile instance")
        entity.delete(this)
        isPersisted = false
    }

    suspend fun refresh() {
        if (!isPersisted) throw SkormException("cannot refresh a volatile instance")
        val self = entity.fetch(this) ?: throw SkormException("cannot refresh instance, it doesn't exist")
        putFields(self)
    }

    // dirty flags handling

    fun setClean() {
        dirtyFields.clear()
        isPersisted = true
    }

    fun isDirty() = dirtyFields.nextSetBit(0) != -1

    override fun put(key: String, value: Any?): Any? {
        val ret = super.put(key, value)
        // coercitive version
        val field = entity.fields[key] ?: throw SkormException("${entity.name} has no field named $key")
        if (isPersisted && field.isPrimary && ret != value) // CB TODO - since 'value' type is lax, 'value' may need a proper conversion before the comparison
                isPersisted = false
        dirtyFields.set(entity.fieldIndices[key]!!, true)
        // relaxed version
        // val field = entity.fields[key]?.let { field ->
        //     if (persisted && field.primary && ret != value) // CB TODO - since 'value' type is lax, 'value' may need a proper conversion before the comparison
        //         persisted = false
        //     dirtyFields.set(entity.fieldIndices[key]!!, true)
        // }
        return ret
    }

    fun putFields(from: Map<out String, Any?>) {
        from.entries.filter { entity.fields.contains(it.key) }.forEach {
            put(it.key, it.value)
        }
    }

    open fun putRawFields(from: Map<out String, Any?>) {
        from.entries.forEach {
            val fieldName = processor.downstreamMapping(it.key)
            entity.fields[fieldName]?.also { field ->
                putRawField(field, it.value)
            } ?: {
                putRawValue(fieldName, it.value)
            }
        }
    }

    // to allow subclasses to add key-value pairs besides entity columns
    fun putRawValue(key: String, value: Any?): Any? = super.put(key, value)

    fun putRawField(field: Field, value: Any?) {
        super.put(field.name, processor.downstreamFilter(field.type, value))
    }

    suspend inline fun <reified T: Any?> eval(attrName: String, vararg params: Any?) = entity.eval<T>(attrName, this, *params)
    suspend inline fun <reified T: Json.Object?> retrieve(attrName: String, vararg params: Any?) = entity.retrieve<T>(attrName, this, *params)
    suspend inline fun <reified T: Json.Object> query(attrName: String, vararg params: Any?) = entity.query<T>(attrName, this, *params)
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
