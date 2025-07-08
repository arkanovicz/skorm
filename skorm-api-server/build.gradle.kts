@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

description = "Skorm API server"

plugins {
    alias(libs.plugins.jvm)
     java
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":skorm-common"))
    implementation(project(":skorm-core"))
    implementation(libs.ktor.server.core)
    implementation(libs.kotlin.logging)
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

