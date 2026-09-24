package com.republicate.skorm.core

import kotlin.coroutines.CoroutineContext

/** Where a connector's blocking calls run by default: a dispatcher meant for blocking IO where the platform has one. */
expect val ioContext: CoroutineContext
