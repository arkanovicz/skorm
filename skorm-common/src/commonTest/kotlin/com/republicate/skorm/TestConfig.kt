package com.republicate.skorm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestConfig {

    class Foo: Configurable {
        override val config = Configuration()
        override fun initialize() {}

        val noNullNoDef by config<String>()
        val noNullDef by config<String>("noNullDef")
        val nullNoDef by config<String?>()
        val nullDef by config<String?>("nullDef")
        val sub by configPath<String>("foo.bar")
    }

    @Test
    fun testConfig() {
        val foo = Foo()
        val cfg = mapOf<String, Any?>()
        foo.configure(cfg)
        try {
            println("foo.noNullNoDef = ${foo.noNullNoDef}")
            throw RuntimeException("previous statement should throw a NullPointerException (jvm/native) or a ClassCastException (js)")
        } catch (npe: NullPointerException) {
        } catch (cce: ClassCastException) {
        }
        foo.configure(mapOf("noNullNoDef" to "someValue"))
        assertEquals("someValue", foo.noNullNoDef)
        assertEquals("noNullDef", foo.noNullDef)
        assertNull(foo.nullNoDef)
        assertEquals("nullDef", foo.nullDef)
        val bar = Foo()
        val cfg2 = mapOf<String, Any?>(
            "noNullNoDef" to "noNullNoDef",
            "noNullDef" to "noNullDef",
            "nullNoDef" to "nullNoDef",
            "nullDef" to "nullDef",
        )
        bar.configure(cfg2)
        assertEquals("noNullNoDef", bar.noNullNoDef)
        assertEquals("noNullDef", bar.noNullDef)
        assertEquals("nullNoDef", bar.nullNoDef)
        assertEquals("nullDef", bar.nullDef)
    }

    @Test
    fun testConfigPath() {
        val foo = Foo()
        val cfg = mapOf<String, Any?>(
            "foo.bar" to "baz"
        )
        foo.configure(cfg)
        assertEquals("baz", foo.sub)
    }
}
