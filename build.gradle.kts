import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
    alias(libs.plugins.nexusPublish)
}

allprojects {

    group = "com.republicate.skorm"
    version = "0.4"

    repositories {
        mavenLocal()
        mavenCentral()
        // maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap/")
    }

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")
//    apply(plugin = "maven-publish")

    tasks {
//        register<Jar>("sourcesJar") {
//            archiveClassifier.set("sources")
//            dependsOn("classes")
//            from(sourceSets["main"].allSource)
//        }
        register<Jar>("dokkaJar") {
            from(dokkaHtml)
            dependsOn(dokkaHtml)
            archiveClassifier.set("javadoc")
        }

        // done project by project
//        withType<JavaCompile>().configureEach {
//            sourceCompatibility = JavaVersion.VERSION_1_8.toString()
//            targetCompatibility = JavaVersion.VERSION_1_8.toString()
//        }

        // seems to break generated tasks in subprojects
//        withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
//            kotlinOptions {
//                this.apiVersion = "1.5"
//                this.languageVersion = "1.5"
//            }
//        }
    }

    /*
    tasks.withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xopt-in=io.ktor.util.KtorExperimentalAPI"
        )
    }
    */
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
