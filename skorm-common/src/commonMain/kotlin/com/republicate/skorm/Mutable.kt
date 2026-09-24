package com.republicate.skorm

/*
 * The mutable half of the runtime: a read-only database is made of plain holders and hands out plain
 * instances; a mutable one is made of holders implementing these, and hands out instances that write.
 */

/** A holder that registers mutations and performs them. */
interface MutableAttributeHolder {
    suspend fun perform(attrName: String, vararg params: Any?): Long =
        (this as AttributeHolder).let { it.mutate(it.findAttribute<Long>(attrName), *params) }
    suspend fun perform(attribute: Attribute<Long>, vararg params: Any?): Long = (this as AttributeHolder).mutate(attribute, *params)
}

interface MutableDatabase : MutableAttributeHolder

interface MutableSchema : MutableAttributeHolder

/** An entity whose rows can be inserted, updated and deleted; its mutations execute with explicit parameters here, or from a [MutableInstance]. */
interface MutableEntity {
    suspend fun perform(attrName: String, vararg params: Any?): Long =
        (this as Entity).instanceAttributes.let { it.mutate(it.findAttribute<Long>(attrName), *params) }
}

private val MutableInstance.self get() = this as MutableInstanceImpl

/** A row that can be written and sent back: it unlocks the storage's map mutators and adds the database writes. */
interface MutableInstance : Instance {

    /** the typed write: the key must be a field; the row becomes dirty, and volatile again if a key column changed */
    fun put(key: String, value: Any?): Any?
    fun dirtyFieldNames(): Iterator<String>

    fun putFields(from: Map<out String, Any?>) {
        from.entries.filter { self.entity.fields.contains(it.key) }.forEach {
            self.put(it.key, it.value)
        }
    }

    suspend fun insert() = with(self) {
        if (isPersisted) throw SkormException("cannot insert a persisted instance")
        if (generatedPrimaryKey && containsKey(entity.primaryKey.first().name)) throw SkormException("generated primary key value cannot be specified at insertion")
        val ret = entity.insert(this)
        if (generatedPrimaryKey) put(entity.primaryKey.first().name, ret)
        else if (ret != 1L) throw SkormException("unexpected number of changed rows, expected 1, found $ret")
        setClean()
    }

    suspend fun update() = with(self) {
        if (!isPersisted) throw SkormException("cannot update a volatile instance")
        entity.update(this)
        setClean()
    }

    suspend fun upsert() = if (self.isPersisted) update() else insert()

    suspend fun delete() = with(self) {
        if (!isPersisted) throw SkormException("cannot delete a volatile instance")
        entity.delete(this)
        isPersisted = false
    }

    suspend fun perform(attrName: String, vararg params: Any?): Long =
        self.entity.instanceAttributes.let { it.mutate(it.findAttribute<Long>(attrName), self, *params) }
}
