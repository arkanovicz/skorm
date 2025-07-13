@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import kotlin.reflect.KClass
import kotlin.reflect.KType

description = "Skorm Bookshelf example project"

plugins {
    alias(libs.plugins.multiplatform)
    id("com.republicate.skorm")
}

buildscript {
    repositories {
        mavenCentral()
    }
}

skorm {
    structure.set(File("src/commonMain/model/bookshelf.kddl"))
    destPackage.set("com.republicate.skorm.bookshelf")
    runtimeModel.set(File("src/commonMain/model/bookshelf.ksql"))
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
    js {
        browser {
            testTask {
                useKarma {
                    // useFirefox()
                    useChromeHeadless()
                    // or, for debugging
                    //useDebuggableChrome()
                    /*
                    webpackConfig.cssSupport {
                        enabled.set(true)
                    }*/
                }
            }
        }
        nodejs()
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xir-minimized-member-names=false")
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(file("build/generated-src/commonMain/kotlin"))
            dependencies {
                implementation("com.republicate.skorm:skorm-common")
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
            }
        }
        commonTest  {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            kotlin.srcDir(file("build/generated-src/jvmMain/kotlin"))
            dependencies {
                implementation("com.republicate.skorm:skorm-api-server")
                implementation("com.republicate.skorm:skorm-core")
                implementation("com.republicate.skorm:skorm-jdbc")
                implementation(libs.kotlinx.html)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.html.builder)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.kotlin.logging)
                implementation(libs.slf4j.simple)
                runtimeOnly(libs.h2)
                runtimeOnly(libs.slf4j.simple)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.server.test.host)
                implementation(libs.jsoup)
            }
        }
        jsMain {
            kotlin.srcDir(file("build/generated-src/jsMain/kotlin"))
            dependencies {
                implementation("com.republicate.skorm:skorm-api-client")
                implementation(libs.kotlinx.coroutines)
            }
        }
        jsTest
    }
}

tasks.named<Copy>("jvmProcessResources") {
    /* for kotlin 2.1.0 ?
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution")
    from(jsBrowserDistribution)
     */
    /* ?!!! TODO
    val jsBrowserWebpack = tasks.named("jsBrowserWebpack")
    from(jsBrowserWebpack)
     */
}

tasks.named<Jar>("jvmJar") {
    manifest { attributes["Main-Class"] = "com.republicate.skorm.bookshelf.ServerKt" }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the bookshelf server application"
    val jvmJar = tasks.named<Jar>("jvmJar")
    dependsOn(jvmJar)
    val jarFile = jvmJar.get().archiveFile.get().asFile
    classpath = files(jarFile) + configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("com.republicate.skorm.bookshelf.ServerKt")
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split("\\s+".toRegex())
    }
    environment("SKORM_JDBC_URL", "jdbc:h2:mem:example")
    environment("SKORM_JDBC_USER", "sa")
    environment("SKORM_JDBC_PASS", "")
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn("generateDatabaseCreationScript")
    from (
        fileTree(baseDir="build/generated-src/jvmMain/resources") { include("*.sql") }
    )
}

/*
tasks.named<Jar>("jvmJar") {
    dependsOn("generateDatabaseCreationScript")
    from (
        fileTree(baseDir="build/generated-src/jvmMain/resources") { include("*.sql") }
    )
}
 */

tasks.withType<KotlinCompilationTask<*>>().forEach {
    it.dependsOn("generateSkormObjectsCode")
    it.dependsOn("generateSkormJoinsCode")
    it.dependsOn("generateSkormModelCode")
}

tasks.all {
    if (this.name == "compileCommonMainKotlinMetadata") {
        this.mustRunAfter("generateSkormObjectsCode")
        this.mustRunAfter("generateSkormJoinsCode")
        this.mustRunAfter("generateSkormModelCode")
    }
}

tasks.withType<Test> {
    environment("SKORM_JDBC_URL", "jdbc:h2:mem:example")
    environment("SKORM_JDBC_USER", "sa")
    environment("SKORM_JDBC_PASS", "")
}
