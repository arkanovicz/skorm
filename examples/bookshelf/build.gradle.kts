plugins {
    kotlin("multiplatform")
    application
}

group = "com.republicate.skorm"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
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
            dependencies {
                implementation(project(":skorm-common"))
//                implementation("io.github.microutils:kotlin-logging:2.0.11")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":skorm-jdbc"))
                implementation("io.ktor:ktor-server-netty:1.6.5")
                implementation("io.ktor:ktor-html-builder:1.6.5")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
                implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
                runtimeOnly("org.slf4j:slf4j-simple:1.7.32")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-server-tests:1.6.5") {
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
    mainClass.set("com.republicate.application.ServerKt")
}

tasks.named<Copy>("jvmProcessResources") {
    val jsBrowserDistribution = tasks.named("jsBrowserDistribution")
    from(jsBrowserDistribution)
}

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
}
