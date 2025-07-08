@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import kotlin.reflect.KClass
import kotlin.reflect.KType

description = "Skorm Bookshelf example project"

plugins {
    alias(libs.plugins.multiplatform)
    application
    id("skorm-gradle-plugin")
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
    /*
    applyDefaultHierarchyTemplate {
        common {
            group("commonJs") {
                withJs()
                withWasmJs()
            }
        }
    }
     */
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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
        compilations.all { compileKotlinTask.kotlinOptions.freeCompilerArgs += listOf("-Xir-minimized-member-names=false") }
    }
    /*
    js(IR) {
        useCommonJs()
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            compilations.all { compileKotlinTask.kotlinOptions.freeCompilerArgs += listOf("-Xir-minimized-member-names=false") }
        }
    }
     */

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(file("build/generated-src/commonMain/kotlin"))
            dependencies {
                implementation("com.republicate.skorm:skorm-common")
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
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
                implementation(libs.hsqldb)
                implementation(libs.slf4j.simple)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.server.test.host)
                implementation(libs.jsoup)
                implementation(libs.h2)
                implementation(libs.slf4j.simple)
            }
        }
        val jsMain by getting {
            kotlin.srcDir(file("build/generated-src/jsMain/kotlin"))
            dependencies {
                implementation("com.republicate.skorm:skorm-api-client")
                implementation(libs.kotlinx.coroutines)
            }
        }
        val jsTest by getting
    }
}

application {
    mainClass.set("com.republicate.skorm.bookshelf.ServerKt")
}

tasks.all {
    val classes = mutableSetOf<Class<*>>()
    var cls: Class<*> = this::class.java
    while (true) {
        classes.add(cls)
        val interfaces = cls.interfaces.toSet()
        classes.addAll(interfaces)
        val sup = cls.superclass
        if (sup == null || classes.contains(sup)) { break }
        cls = sup
    }
    println("@@@@@@@@@@@ " + this + " with classes ${
        classes.joinToString(" / ")
    }")
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

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
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

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().forEach {
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
