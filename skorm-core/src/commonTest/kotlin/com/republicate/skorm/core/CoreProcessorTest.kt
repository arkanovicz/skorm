package com.republicate.skorm.core

import com.republicate.skorm.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoreProcessorTest {

    private object NoConnector : Connector {
        override val config = Configuration()
        override fun getMetaInfos() = object : MetaInfos {
            override val identifierQuoteChar = '"'
            override val identifierInternalCase = 'L'
            override val strictColumnTypes = false
            override val columnMarkers = false
        }
        override fun query(schema: String, query: String, vararg params: Any?) = throw UnsupportedOperationException()
        override fun mutate(schema: String, query: String, vararg params: Any?) = throw UnsupportedOperationException()
        override fun begin(schema: String) = throw UnsupportedOperationException()
        override fun close() {}
    }

    /** the mappers are live state: a database handed to a template must not reconfigure them */
    @Test
    fun configureRefusedOnceInitialized() {
        val processor = CoreProcessor(NoConnector)
        processor.configure(mapOf())
        processor.initialize()
        assertFailsWith<SkormException> { processor.configure(mapOf()) }
        assertFailsWith<SkormException> { processor.readOnly.configure(mapOf()) }
    }
}
