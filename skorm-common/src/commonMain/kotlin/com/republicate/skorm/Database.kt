package com.republicate.skorm

import com.republicate.kson.Json

open class Database protected constructor(name: String, processor: Processor): AttributeHolder(name), Configurable, AutoCloseable {
    override val database get() = this

    /** a read-only database holds a processor that cannot write, whoever asks it */
    override val processor: Processor = if (this is MutableDatabase) processor else processor.readOnly

    /** where blocking twins dispatch their query; by default the calling thread itself */
    var blockingContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext
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

    override val database: Database get() = parent as Database
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

open class Entity protected constructor(val name: String, val schema: Schema, val parent: Entity? = null) : RowFactory {

    /**
     * What the entity's own SELECTs read from, when not its table: a hierarchy root reads its table
     * LEFT JOINed with its descendants' base tables, so that every row comes back complete and as its kind.
     */
    open val source: String? = null

    init {
        @Suppress("LeakingThis")
        schema.addEntity(this)
    }

    inner class InstanceAttributes: AttributeHolder(name, schema) {
        override val processor get() = schema.processor
        override val schema get() = parent as Schema
        // a subtype answers to its parent entity's attributes: navigations, ksql attributes, all of it
        override val inherited: AttributeHolder? get() = this@Entity.parent?.instanceAttributes
        override val mutable: Boolean get() = this@Entity is MutableEntity

        override fun prepare(attr: Attribute<*>, vararg params: Any?): Pair<String, Map<String, Any?>> {
            val doRestPK = processor.restMode && params.isNotEmpty() && params[0] is Instance && (params[0] as Instance).isPersisted
            return if (doRestPK) {
                val instance = params[0] as Instance
                val pkFields = instance.entity.primaryKey.map { it.name }
                val execPath = "${ownerPath(attr)}/${
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

    private val fetchAttribute: NullableRowAttribute<Instance> by lazy {
        NullableRowAttribute<Instance>("fetch", primaryKey.map { it.name }.toSet(), this).apply {
            check(schema.database.populated)
        }
    }

    private val browseAttribute: RowSetAttribute<Instance> by lazy {
        RowSetAttribute<Instance>("browse", emptySet(), this).apply {
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

    override fun new(): Instance = InstanceImpl(this)

    /** A hierarchy root overrides this to build the subclass [kind] names; rows of other entities carry no kind. */
    override fun new(kind: String?): Instance = new()

    open suspend fun fetch(vararg key: Any): Instance? = instanceAttributes.retrieve(fetchAttribute, *key)
    open suspend fun browse() = instanceAttributes.query<Instance>(browseAttribute)
    open suspend operator fun iterator() = browse().iterator()

    // Other operations are not visible directly, they are proxied from MutableInstance
    internal suspend fun insert(instance: Instance): Long {
        return if (primaryKey.size == 1 && primaryKey.first().isGenerated) {
            // Convert property name to database column name
            val dbColName = instanceAttributes.processor.upstreamMapping(primaryKey.first().name)
            instanceAttributes.mutate(insertAttribute, instance, GeneratedKeyMarker(dbColName))
        } else {
            instanceAttributes.mutate(insertAttribute, instance)
        }
    }
    internal suspend fun update(instance: Instance) = instanceAttributes.mutate(updateAttribute, instance)
    internal suspend fun delete(instance: Instance) = instanceAttributes.mutate(deleteAttribute, instance)
    /*internal*/ suspend inline fun <reified T: Any?> eval(attrName: String, vararg params: Any?) = instanceAttributes.eval<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Row?> retrieve(attrName: String, vararg params: Any?) = instanceAttributes.retrieve<T>(attrName, *params)
    /*internal*/ suspend inline fun <reified T: Row> query(attrName: String, vararg params: Any?) = instanceAttributes.query<T>(attrName, *params)
}
