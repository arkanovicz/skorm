plugins {
    `java-library`
    `maven-publish`
}

val kotlin_version: String by project
val datetime_version: String by project

dependencies {
    api(project(":skorm-core"))
    //implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation(libs.slf4j.api)
    implementation(libs.commons.lang3)
    implementation(libs.kotlinx.datetime)
    implementation(libs.commons.collections4)


    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    // testImplementation("org.mockito:mockito-junit-jupiter:4.0.0")

    testRuntimeOnly(libs.slf4j.simple)
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly(libs.h2)
}

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

    getByName<Test>("test") {
        useJUnitPlatform()
    }
}
*/

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

