@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

description = "Skorm Gradle Plugin"

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.antlr)
    `java-gradle-plugin`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

gradlePlugin {
  plugins {
    create("skormGradlePlugin") {
      id = "com.republicate.skorm"
      displayName = "Skorm Gradle Plugin"
      implementationClass = "com.republicate.skorm.SkormGradlePlugin"
    }
  }
}

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath(libs.antlr.kotlin)
    }
}

tasks.register<com.strumenta.antlrkotlin.gradle.AntlrKotlinTask>("generateKotlinGrammarSource") {
    // maxHeapSize = "64m"
    packageName = "com.republicate.skorm.parser"
    arguments = listOf("-no-visitor", "-no-listener")
    source = project.objects
        .sourceDirectorySet("antlr", "antlr")
        .srcDir("src/main/antlr").apply {
            include("*.g4")
        }
    outputDirectory = layout.buildDirectory.dir("generated-src/main/kotlin").get().asFile
    group = "code generation"
}

kotlin {
    sourceSets {
        main {
            // the task's output, so compilation, the sources jar and Dokka all depend on it
            kotlin.srcDir(tasks.named("generateKotlinGrammarSource"))
        }
    }
    jvmToolchain(21)
}

dependencies {
    implementation(project(":skorm-common"))
    implementation(project(":skorm-core"))
    implementation(libs.velocity.engine.core)
    implementation(libs.velocity.tools.generic)
    implementation(libs.evo.inflector)
    api(libs.kddl)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(gradleTestKit())
    // as api to expose CharStream
    api(libs.antlr.kotlin)
}

tasks {
    named<KotlinCompilationTask<*>>("compileKotlin").configure {
        compilerOptions {
            apiVersion.set(KotlinVersion.KOTLIN_2_2)
            languageVersion.set(KotlinVersion.KOTLIN_2_2)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    // GoldenOutputTest rewrites its expected files under this variable: a run must not be skipped as up-to-date
    inputs.property("SKORM_UPDATE_GOLDEN", providers.environmentVariable("SKORM_UPDATE_GOLDEN").orElse(""))
}
