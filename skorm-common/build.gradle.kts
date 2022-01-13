plugins {
    kotlin("multiplatform")
}

// group = "com.republicate.skorm"
// version = "1.0-SNAPSHOT"

// repositories {
//     mavenCentral()
    // mavenLocal()
// }

buildscript {
    val atomicfu_version: String by project
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
    }
}
apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val atomicfu_version: String by project
        val commonMain by getting {
            dependencies {
                api("com.republicate.kson:essential-kson:1.3")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicfu_version")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // TODO - another wizard wanted this... 
                // implementation(kotlin("test-common"))
                //implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting
    }
}
