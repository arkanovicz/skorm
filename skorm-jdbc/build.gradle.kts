description = "Skorm JDBC connector"

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.jvm) // for Kotlin tests
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

dependencies {
    api(project(":skorm-core"))
    implementation(libs.slf4j.api)
    implementation(libs.commons.lang3)
    implementation(libs.kotlinx.datetime)
    implementation(libs.commons.collections4)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.coroutines.test)

    testRuntimeOnly(libs.slf4j.simple)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

