package com.republicate.skorm

/** Marks what only a processor should call: the raw row loading, which bypasses the typed write path. */
@RequiresOptIn(message = "Raw row loading, meant for processors: it bypasses the typed write path", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class SkormInternalApi
