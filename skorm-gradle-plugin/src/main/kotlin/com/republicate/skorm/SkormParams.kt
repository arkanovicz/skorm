package com.republicate.skorm

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

const val DEFAULT_OUTPUT_PATH = "generated-src/kotlin"
const val DEFAULT_OUTPUT_STRUCTURE_FILE = "skormObjects.kt"
const val DEFAULT_OUTPUT_PROPERTIES_FILE = "skormProperties.kt"

@Suppress("UnnecessaryAbstractClass")
abstract class SkormParams @Inject constructor(project: Project) {

    private val objects = project.objects

    val modelStructure: RegularFileProperty = objects.fileProperty()

    val datasource: Property<String> = objects.property(String::class.java)

    val modelProperties: RegularFileProperty = objects.fileProperty()

    val destPackage: Property<String> = objects.property(String::class.java)

    val destStructureFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_OUTPUT_PATH/$DEFAULT_OUTPUT_STRUCTURE_FILE")
    )

    val destPropertiesFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_OUTPUT_PATH/$DEFAULT_OUTPUT_PROPERTIES_FILE")
    )

}
