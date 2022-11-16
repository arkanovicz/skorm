import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.*

plugins {
    kotlin("jvm") version "1.3.31"
    id("org.gradle.kotlin.kotlin-dsl") version "1.2.8"
    `maven-publish`
    id("java-gradle-plugin")
    id("com.jfrog.bintray") version "1.8.4"
}

group = "com.ccfraser.gradle"
version = "0.3.1"
description = "A gradle plugin to help with builds related to webpack to try and reduce boilerplate code in build files."

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val publicationName = "mavenJava"

//tasks.register<Jar>("sourcesJar") {
//    from(sourceSets.main.get().allSource)
//    archiveClassifier.set("sources")
//}

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>(publicationName) {
            from(components["java"])
//            artifact(tasks["KDocJar"])
//            artifact(tasks["sourcesJar"])

            pom {
                name.set("Gradle Webpack Plugin")
                description.set(project.description)
//                url.set("https://github.com/cfnz/muirwik")
                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                    }
                }
//                scm {
//                    connection.set("https://github.com/cfnz/muirwik.git")
//                    url.set("https://github.com/cfnz/muirwik")
//                }
            }
        }
    }
}

bintray {
    // Bintray keys are kept in a local, non version controlled, properties file
    if (project.file("local.properties").exists()) {
        val properties = Properties()
        properties.load(FileInputStream(project.file("local.properties")))
        fun findProperty(propertyName: String) = properties[propertyName] as String?

        user = findProperty("bintray.user")
        key = findProperty("bintray.apikey")
        publish = true
        override = true

        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            // Mandatory fields
            repo = project.name
            name = "${project.group}:${project.name}"
            setLicenses("MPL-2.0")
            vcsUrl = "https://github.com/cfnz/gradle-webpack-plugin"

            // Some optional fields
            description = project.description
            desc = description
            websiteUrl = "https://github.com/cfnz/gradle-webpack-plugin"
            issueTrackerUrl = "https://github.com/cfnz/gradle-webpack-plugin/issues"
            githubRepo = "https://github.com/cfnz/gradle-webpack-plugin"
            setLabels("kotlin", "webpack", "material-ui", "react")
        })
        setPublications(publicationName)
    } else {
        println("Could not find local.properties file")
    }
}
