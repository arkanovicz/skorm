description = "Skorm JDBC connector"

plugins {
    `java-library`
    `maven-publish`
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

    testRuntimeOnly(libs.slf4j.simple)
    testRuntimeOnly(libs.h2)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

