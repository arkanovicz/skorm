plugins {
    kotlin("multiplatform")
}

// version = "1.0-SNAPSHOT"

// repositories {
//     mavenCentral()
// }

val ktor_version: String by project

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
//         hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }
    jvm {
        compilations.all {
            // kotlin compiler compatibility options
            kotlinOptions.jvmTarget = "1.8"
        }
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useDebuggableChrome()
                    //useChromeHeadless()
                    //useFirefox()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
        // nodejs() what?!
    }

    sourceSets {

        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktor_version")
                api(project(":skorm-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}
