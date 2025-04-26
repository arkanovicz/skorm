plugins {
    // alias(libs.plugins.jvm)
    id("org.jetbrains.kotlin.jvm")
     java
    `maven-publish`
}

val ktor_version: String by project

// TODO specify kotlin language level and jvm version

/*
tasks {
    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
        kotlinOptions {
            languageVersion = "1.7"
            apiVersion = "1.7"
        }
    }
    withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}
*/

dependencies {
    implementation(project(":skorm-common"))
    implementation(project(":skorm-core"))
    implementation(libs.ktor.server.core)
    implementation(libs.kotlin.logging)
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

