package com.republicate.skorm

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BlockingJsTest {
    @Test
    fun blockingIsRefusedOnJs() {
        assertFailsWith<UnsupportedOperationException> { blockingOn(EmptyCoroutineContext) { 1 } }
    }
}
