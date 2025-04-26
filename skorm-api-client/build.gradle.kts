import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    // alias(libs.plugins.multiplatform)
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("commonJs") {
                withJs()
                withWasmJs()
            }
        }
    }

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js {
        browser {
            testTask {
                useKarma {
                    useDebuggableChrome()
                    //useChromeHeadless()
                    // useFirefox()
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
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()
    linuxArm64()
    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()
    // watchosArm32()
    watchosArm64()
    // watchosDeviceArm64()
    watchosX64()
    watchosSimulatorArm64()
    mingwX64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    // Waiting for next version of kotlinx-datetime
    // wasmWasi()

    sourceSets {

        val commonMain by getting {
            dependencies {
                api(project(":skorm-common"))
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.ktor.client.core)
                // implementation("io.ktor:ktor-client-serialization:$ktor_version")
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val commonJsMain by getting
        val commonJsTest by getting
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val wasmJsMain by getting
        val wasmJsTest by getting

        all {
            // languageSettings.enableLanguageFeature("InlineClasses")
            // languageSettings.optIn("expect-actual-classes")
            // languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
        }

    }
}
