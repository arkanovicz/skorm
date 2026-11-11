description = "Skorm club example project: a kddl schema importing the bookshelf's"

plugins {
    alias(libs.plugins.jvm)
    id("com.republicate.skorm")
}

skorm {
    model.set(file("src/main/model/club.kddl"))
    attributes.set(file("src/main/model/club.ksql"))
    destPackage.set("com.republicate.skorm.club")
    dialect.set("hypersql")
    // the bookshelf's jar ships its kddl schema and where its classes are
    imports.from(configurations.runtimeClasspath)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":bookshelf"))
    implementation("com.republicate.skorm:skorm-common")
    implementation("com.republicate.skorm:skorm-core")
    implementation("com.republicate.skorm:skorm-jdbc")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    forkEvery = 1 // the generated singletons allow one database pair per JVM
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
