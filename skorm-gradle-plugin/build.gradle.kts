@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

description = "Skorm Gradle Plugin"

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.antlr)
    `java-gradle-plugin`
    `maven-publish`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

gradlePlugin {
  plugins {
    create("skormGradlePlugin") {
      id = "com.republicate.skorm"
      displayName = "Skorm Gradle Plugin"
      implementationClass = "com.republicate.skorm.SkormGradlePlugin"
      version = "0.8.2"
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

kotlin {
    sourceSets{
        main {
            kotlin.srcDirs(
                file("${layout.buildDirectory.get()}/generated-src/main/kotlin"),
            )
        }
        test
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

tasks.register<com.strumenta.antlrkotlin.gradle.AntlrKotlinTask>("generateKotlinGrammarSource") {
    // maxHeapSize = "64m"
    packageName = "com.republicate.skorm.parser"
    arguments = listOf("-no-visitor", "-no-listener")
    source = project.objects
        .sourceDirectorySet("antlr", "antlr")
        .srcDir("src/main/antlr").apply {
            include("*.g4")
        }
    outputDirectory = File("build/generated-src/main/kotlin")
    group = "code generation"

}

tasks.filter { it.name.startsWith("compileKotlin") }.forEach { it.dependsOn("generateKotlinGrammarSource") }
tasks.findByName("dokkaHtml")?.dependsOn("generateKotlinGrammarSource")
tasks.findByName("sourcesJar")?.dependsOn("generateKotlinGrammarSource")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("test") {
    doFirst {
        println("Compiled test classes:")
        fileTree("build/classes/kotlin/test").forEach { println(it) }
    }
}
