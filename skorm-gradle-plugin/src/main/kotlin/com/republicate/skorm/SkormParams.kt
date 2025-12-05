package com.republicate.skorm

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

const val DEFAULT_COMMON_OUTPUT_PATH = "generated-src/commonMain/kotlin"
const val DEFAULT_JVM_OUTPUT_PATH = "generated-src/jvmMain/kotlin"
const val DEFAULT_JS_OUTPUT_PATH = "generated-src/jsMain/kotlin"
const val DEFAULT_JVM_RESOURCES_PATH = "generated-src/jvmMain/resources"
const val DEFAULT_OUTPUT_STRUCTURE_FILE = "skormObjects.kt"
const val DEFAULT_OUTPUT_JOINS_FILE = "skormJoins.kt"
const val DEFAULT_OUTPUT_JOINS_CORE_FILE = "skormJoinsCore.kt"
const val DEFAULT_OUTPUT_JOINS_CLIENT_FILE = "skormJoinsClient.kt"
const val DEFAULT_OUTPUT_MODEL_FILE = "skormModel.kt"
const val DEFAULT_OUTPUT_MODEL_CORE_FILE = "skormModelCore.kt"
const val DEFAULT_OUTPUT_MODEL_CLIENT_FILE = "skormModelClient.kt"
const val DEFAULT_OUTPUT_CREATION_SCRIPT_FILE = "create-script.sql"

@Suppress("UnnecessaryAbstractClass")
abstract class SkormParams @Inject constructor(project: Project) {

    private val objects = project.objects

    val structure: RegularFileProperty = objects.fileProperty()

    val datasource: Property<String> = objects.property(String::class.java)

    val properties: RegularFileProperty = objects.fileProperty()

    val destPackage: Property<String> = objects.property(String::class.java)

    val destStructureFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_STRUCTURE_FILE")
    )

    val destJoinsFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_JOINS_FILE")
    )

    val destCoreJoinsFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_JVM_OUTPUT_PATH/$DEFAULT_OUTPUT_JOINS_CORE_FILE")
    )

    val destClientJoinsFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_JS_OUTPUT_PATH/$DEFAULT_OUTPUT_JOINS_CLIENT_FILE")
    )

    val runtimeModel: RegularFileProperty = objects.fileProperty()

    val destModelFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_MODEL_FILE")
    )

    val destCoreModelFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_JVM_OUTPUT_PATH/$DEFAULT_OUTPUT_MODEL_CORE_FILE")
    )

    val destClientModelFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_JS_OUTPUT_PATH/$DEFAULT_OUTPUT_MODEL_CLIENT_FILE")
    )

//    val destPopulateFile: RegularFileProperty = objects.fileProperty().convention(
//        project.layout.buildDirectory.file("$DEFAULT_JVM_OUTPUT_PATH/$DEFAULT_OUTPUT_POPULATE_FILE")
//    )
//
//    val destPropertiesFile: RegularFileProperty = objects.fileProperty().convention(
//        project.layout.buildDirectory.file("$DEFAULT_COMMON_OUTPUT_PATH/$DEFAULT_OUTPUT_PROPERTIES_FILE")
//    )

    val destCreationScriptFile: RegularFileProperty = objects.fileProperty().convention(
        project.layout.buildDirectory.file("$DEFAULT_JVM_RESOURCES_PATH/$DEFAULT_OUTPUT_CREATION_SCRIPT_FILE")
    )

    val dialect: Property<String> = objects.property(String::class.java)
}
