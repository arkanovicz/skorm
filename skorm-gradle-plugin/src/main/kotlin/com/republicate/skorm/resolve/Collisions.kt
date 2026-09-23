package com.republicate.skorm.resolve

import com.republicate.skorm.Instance
import com.republicate.skorm.SkormException

/** Names a generated member cannot take, and the spelling a keyword needs. */
object Collisions {

    private val keywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
        "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while"
    )

    /**
     * Every member a generated row inherits, as a Kotlin caller sees it: `size`, `entity`, `put`, `isPersisted`…
     *
     * A static list is a temporary workaround for Gradle 8.x: a plugin runs on the Kotlin stdlib Gradle embeds
     * (2.0.21 throughout 8.x), which lacks types such as `kotlin.time.Instant` that `Instance`'s signatures
     * mention through kson, so reflecting over `Instance` at generation time fails with NoClassDefFoundError.
     * `ResolverTest` recomputes the list reflectively in the plugin's own test JVM, so it cannot drift unnoticed.
     * Once consumers build on Gradle 9 (stdlib 2.2+), replace the list with that reflection.
     */
    val inherited: Set<String> = setOf(
        "asArray", "asObject", "class", "clear", "compute", "computeIfAbsent", "computeIfPresent", "containsKey", "containsValue",
        "copy", "delete", "dirtyFields", "ensureIsArray", "ensureIsObject", "entity", "entries", "entrySet", "equals",
        "eval", "forEach", "generatedPrimaryKey", "get", "getArray", "getAs", "getBigDecimal", "getBigInteger", "getBoolean",
        "getByte", "getBytes", "getChar", "getClass", "getDirtyFields", "getDouble", "getEntity", "getEntries", "getFloat",
        "getGeneratedPrimaryKey", "getInstant", "getInt", "getInteger", "getJson", "getKeys", "getLocalDate", "getLocalDateTime", "getLocalTime",
        "getLong", "getObject", "getOrDefault", "getShort", "getSize", "getString", "getUuid", "getValues", "hashCode",
        "insert", "isArray", "isDirty", "isEmpty", "isMutable", "isObject", "isPersisted", "iterator", "keySet",
        "keys", "merge", "notify", "notifyAll", "perform", "put", "putAll", "putFields", "putIfAbsent",
        "putRawField", "putRawFields", "putRawValue", "query", "refresh", "remove", "replace", "replaceAll", "retrieve",
        "set", "setAll", "setClean", "setPersisted", "size", "spliterator", "toPrettyString", "toString", "update",
        "upsert", "values", "wait"
    )

    /** The identifier as it must be written in Kotlin. */
    fun identifier(name: String) = if (name in keywords) "`$name`" else name

    fun checkField(entity: String, field: String) {
        if (field in inherited) throw SkormException(
            "table $entity: column '$field' would override a member every row inherits from Instance; rename it or alias it (`as`)")
    }

    fun checkAccessor(receiver: String, name: String, what: String) {
        if (name in inherited) throw SkormException(
            "$receiver: $what '$name' would override a member every row inherits from Instance; rename it")
    }

    /**
     * A hierarchy is read with `SELECT *` over its tables joined: a column declared twice below one root
     * would silently come back once. The key is shared by design (`USING`).
     */
    fun checkHierarchy(root: com.republicate.kddl.ASTTable) {
        if (root.parent != null || root.children.isEmpty()) return
        val seen = mutableMapOf<String, String>()
        fun walk(table: com.republicate.kddl.ASTTable) {
            for (field in table.fields.values) {
                if (field.primaryKey) continue
                val previous = seen.put(field.name, table.name)
                if (previous != null && previous != table.name) throw SkormException(
                    "table ${table.name}: column '${field.name}' is already declared by $previous in the same hierarchy; one name per hierarchy")
            }
            table.children.forEach { walk(it) }
        }
        walk(root)
    }

    /** Two accessors cannot share a receiver and a name, whatever produced them. */
    fun checkUnique(joins: List<JoinAttribute>, attributes: List<QueryAttribute>) {
        val seen = mutableMapOf<Pair<String, String>, String>()
        fun claim(receiver: String, name: String, what: String) {
            val previous = seen.put(receiver to name, what)
            if (previous != null) throw SkormException("$receiver: '$name' is both $previous and $what")
        }
        joins.forEach { claim(it.receiverClass, it.name, "a ${it.label}") }
        attributes.forEach { claim(it.receiverClass, it.name, "the attribute ${it.qualifiedName}") }
    }
}
