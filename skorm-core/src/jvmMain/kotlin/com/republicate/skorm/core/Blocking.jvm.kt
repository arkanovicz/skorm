package com.republicate.skorm.core

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual val ioContext: CoroutineContext = Dispatchers.IO
