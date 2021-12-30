plugins {
    kotlin("jvm")
    java
}

val ktor_version: String by project

tasks {
    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
        kotlinOptions {
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }
    withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation(project(":skorm-jdbc"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

