package com.republicate.skorm

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.generic.LogTool
import java.util.*

object Velocity {
    val engine = VelocityEngine().apply {
        val prop = Properties().apply {
            put("resource.loaders", "class")
            put("resource.loader.class.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader")
            put("velocimacro.library.path", "templates/macros.vtl")
            put("runtime.log.track_location", "true")
        }
        init(prop)
    }

    fun getContext() = VelocityContext().apply {
        put("kotlin", KotlinTool())
        put("log", LogTool())
    }
}