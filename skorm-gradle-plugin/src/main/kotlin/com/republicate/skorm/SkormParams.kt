package com.republicate.skorm

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

const val DEFAULT_COMMON_OUTPUT_PATH = "generated-src/common/kotlin"
const val DEFAULT_CORE_OUTPUT_PATH = "generated-src/core/kotlin"
const val DEFAULT_OUTPUT_STRUCTURE_FILE = "skormObjects.kt"
const val DEFAULT_OUTPUT_POPULATE_FILE = "skormPopulate.kt"
const val DEFAULT_OUTPUT_PROPERTIES_FILE = "skormProperties.kt"

@Suppress("UnnecessaryAbstractClass")
abstract class SkormParams @Inject constructor(project: Project) {

    private val objects = project.objects

    val modelStructure: RegularFileProperty = objects.fileProperty()

    val datasource: Property<String> = objects.property(String::class.java)

    val modelProperties: RegularFileProperty = objects.fileProperty()

    val destPackage: Property<String> = objects.property(String::class.java)

    val destStructureFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_STRUCTURE_FILE")
    )

    val destPopulateFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_CORE_OUTPUT_PATH/$DEFAULT_OUTPUT_POPULATE_FILE")
    )

    val destPropertiesFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_PROPERTIES_FILE")
    )

//    fun database(name: String, action: Action<SkormParams>) {
//        println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ destPackage = ${destPackage.orNull}")
//        action.execute(this)
//    }

}
