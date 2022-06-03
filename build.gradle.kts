plugins {
    kotlin("multiplatform") version "1.6.21" apply false
    kotlin("jvm") version "1.6.21" apply false
    id("org.jetbrains.dokka") version "1.6.21"
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

allprojects {

    group = "com.republicate.skorm"
    version = "0.1"

    repositories {
        mavenCentral()
        // maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap/")
    }

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")

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
