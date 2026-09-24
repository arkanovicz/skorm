package com.republicate.skorm

/*
 * Live read-only views of the model's own maps and collections. The Kotlin types are read-only already, but
 * reflection (Velocity) sees the runtime class: a bare LinkedHashMap would take `$entity.fields.clear()`.
 * On the JVM, the mutators these classes don't implement are stubs that throw UnsupportedOperationException.
 */

internal open class ReadOnlyCollection<E>(private val c: Collection<E>) : Collection<E> by c {
    override fun iterator(): Iterator<E> = c.iterator().let { object : Iterator<E> by it {} }
    override fun equals(other: Any?) = c == other
    override fun hashCode() = c.hashCode()
    override fun toString() = c.toString()
}

internal class ReadOnlySet<E>(s: Set<E>) : ReadOnlyCollection<E>(s), Set<E>

internal class ReadOnlyMap<K, V>(private val map: Map<K, V>) : Map<K, V> by map {
    override val keys: Set<K> get() = ReadOnlySet(map.keys)
    override val values: Collection<V> get() = ReadOnlyCollection(map.values)
    override val entries: Set<Map.Entry<K, V>> get() = ReadOnlySet(map.entries)
    override fun equals(other: Any?) = map == other
    override fun hashCode() = map.hashCode()
    override fun toString() = map.toString()
}
