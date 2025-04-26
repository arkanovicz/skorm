import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    //id("org.jetbrains.kotlin.multiplatform") apply false
    //id("org.jetbrains.kotlin.jvm") apply false
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
    alias(libs.plugins.nexusPublish)
}

allprojects {

    group = "com.republicate.skorm"
    version = "0.4"

    repositories {
        mavenCentral()
    }

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")

    tasks {
        register<Jar>("dokkaJar") {
            from(dokkaHtml)
            dependsOn(dokkaHtml)
            archiveClassifier.set("javadoc")
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

apply(plugin = "io.github.gradle-nexus.publish-plugin")

nexusPublishing {
    repositories {
        sonatype {
            useStaging.set(true)
        }
    }
}
