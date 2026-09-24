package com.republicate.skorm.core

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** single-threaded: nowhere else to run */
actual val ioContext: CoroutineContext = EmptyCoroutineContext
