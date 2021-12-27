plugins {
    kotlin("jvm")
//    id("org.jetbrains.kotlin.js") version "1.5.31"
//    kotlin("jvm") version "1.5.31"
//    java
}

group = "com.republicate.skorm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

 dependencies {
    // implementation(kotlin("stdlib"))
     implementation("io.ktor:ktor-server-core:1.6.5")
     implementation(project(":skorm-jdbc"))
     testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
     testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
