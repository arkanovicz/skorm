package com.republicate.skorm

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

const val DEFAULT_OUTPUT_DIRECTORY = "generated-src"

@Suppress("UnnecessaryAbstractClass")
abstract class SkormParams @Inject constructor(project: Project) {

    private val objects = project.objects

    /** The kddl model; exactly one of [model] or [datasource] is expected. */
    val model: RegularFileProperty = objects.fileProperty()

    /** JDBC URL to reverse-engineer the model from, instead of [model]. */
    val datasource: Property<String> = objects.property(String::class.java)

    /** The ksql attributes declared over the model; without it, no attribute code is generated. */
    val attributes: RegularFileProperty = objects.fileProperty()

    val destPackage: Property<String> = objects.property(String::class.java)

    /** kddl `Format` name — `postgresql` or `hypersql`. Required to emit the creation script. */
    val dialect: Property<String> = objects.property(String::class.java)

    /** Server-side registrations and the creation script. Unset: on when the project has a JVM target. */
    val core: Property<Boolean> = objects.property(Boolean::class.java)

    /** REST client registrations. Unset: on when the project has a JS, wasm or native target. */
    val client: Property<Boolean> = objects.property(Boolean::class.java)

    /** Only the read-only half: no mutable database, no setters, no mutations, for a build that never writes. */
    val readOnly: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    val outputDirectory: DirectoryProperty = objects.directoryProperty().convention(
        project.layout.buildDirectory.dir(DEFAULT_OUTPUT_DIRECTORY)
    )
}
