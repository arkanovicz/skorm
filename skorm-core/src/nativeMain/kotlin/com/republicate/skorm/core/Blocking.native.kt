package com.republicate.skorm.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

actual val ioContext: CoroutineContext = Dispatchers.IO
