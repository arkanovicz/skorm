package com.republicate.skorm

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

const val DEFAULT_OUTPUT_FILE = "test.kt"

@Suppress("UnnecessaryAbstractClass")
abstract class CodeGenParams @Inject constructor(project: Project) {

    private val objects = project.objects

    val source: Property<String> = objects.property(String::class.java)
    val destPackage: Property<String> = objects.property(String::class.java)

    // Example of a property with a default set with .convention
    val destFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file(DEFAULT_OUTPUT_FILE)
    )
}
