plugins {
    kotlin("multiplatform")
    application
    id("skorm-gradle-plugin") version "0.1"
}

buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io") // for antlr-kotlin
    }
    dependencies {
        classpath("com.republicate.skorm:skorm-gradle-plugin:0.1")
    }
}

skorm {
    definition.set(File("src/commonMain/model/bookshelf.kddl"))
//    properties.set(File("src/commonMain/model/bookshelf.ksql"))
    destPackage.set("com.republicate.skorm.bookshelf")

//    database("example") {
//    }
//    destStructureFile.set(File("generated-src/..."))
//    destPropertiesFile.set(File("generated-src/..."))
}

val ktor_version: String by project

kotlin {
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(file("build/generated-src/commonMain/kotlin"))
            dependencies {
                implementation(project(":skorm-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
//                implementation("io.github.microutils:kotlin-logging:2.0.11")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDir(file("build/generated-src/jvmMain/kotlin"))
            dependencies {
                implementation(project(":skorm-api-server"))
                implementation(project(":skorm-core"))
                implementation(project(":skorm-jdbc"))
                implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
                implementation("io.ktor:ktor-server-netty:$ktor_version")
                implementation("io.ktor:ktor-server-html-builder:$ktor_version")
                implementation("io.ktor:ktor-server-status-pages:$ktor_version")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
                implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
                runtimeOnly("org.hsqldb:hsqldb:2.6.1")
                runtimeOnly("org.slf4j:slf4j-simple:1.7.32")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-server-tests:$ktor_version") {
                    exclude(group = "ch.qos.logback", module="logback-core")
                    exclude(group = "ch.qos.logback", module="logback-classic")
                }
                implementation("org.jsoup:jsoup:1.14.3")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(project(":skorm-api-client"))
            }
        }
        val jsTest by getting
    }
}

application {
    mainClass.set("com.republicate.skorm.bookshelf.ServerKt")
}

tasks.named<Copy>("jvmProcessResources") {
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution")
    from(jsBrowserDistribution)
}

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
}

tasks.filter { it.name.startsWith("compileKotlin") }.forEach {
    it.dependsOn("generateSkormObjectsCode")
    // it.dependsOn("generateSkormProcessor")
    it.dependsOn("generateSkormPropertiesCode")
}
