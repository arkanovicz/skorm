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

    /** Model structure; exactly one of [structure] or [datasource] is expected. */
    val structure: RegularFileProperty = objects.fileProperty()

    /** JDBC URL to reverse-engineer the structure from, instead of [structure]. */
    val datasource: Property<String> = objects.property(String::class.java)

    /** Runtime model (ksql); without it, no attribute code is generated. */
    val runtimeModel: RegularFileProperty = objects.fileProperty()

    val destPackage: Property<String> = objects.property(String::class.java)

    /** kddl `Format` name — `postgresql` or `hypersql`. Required to emit the creation script. */
    val dialect: Property<String> = objects.property(String::class.java)

    /** Server-side registrations and the creation script. Unset: on when the project has a JVM target. */
    val core: Property<Boolean> = objects.property(Boolean::class.java)

    /** REST client registrations. Unset: on when the project has a JS, wasm or native target. */
    val client: Property<Boolean> = objects.property(Boolean::class.java)

    val outputDirectory: DirectoryProperty = objects.directoryProperty().convention(
        project.layout.buildDirectory.dir(DEFAULT_OUTPUT_DIRECTORY)
    )
}
