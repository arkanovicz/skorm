@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

description = "Skorm - Simple Kotlin Object-Relational Mapping"

plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
    alias(libs.plugins.nexusPublish)
    alias(libs.plugins.versions)
}

allprojects {

    group = "com.republicate.skorm"
    version = "0.8"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    afterEvaluate {
        publishing {
            publications.withType<MavenPublication> {
                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/arkanovicz/skorm")
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cbrisson")
                            name.set("Claude Brisson")
                            email.set("claude.brisson@gmail.com")
                            organization.set("republicate.com")
                            organizationUrl.set("https://republicate.com")
                        }
                    }
                    scm {
                        connection.set("scm:scm:git@github.com/arkanovicz/skorm.git")
                        url.set("https://github.com/arkanovicz/skorm")
                    }
                }
                // Task ':publish<platform>PublicationToMavenLocal' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency.
                // => generate dokkaJar tasks for each platform, instead of declaring: artifact(tasks["dokkaJar"])
                val dokkaJar = project.tasks.register("${name}DokkaJar", Jar::class) {
                    group = JavaBasePlugin.DOCUMENTATION_GROUP
                    description = "Assembles Kotlin docs with Dokka into a Javadoc jar"
                    archiveClassifier.set("javadoc")
                    from(tasks.named("dokkaHtml"))

                    // Each archive name should be distinct, to avoid implicit dependency issues.
                    // We use the same format as the sources Jar tasks.
                    // https://youtrack.jetbrains.com/issue/KT-46466
                    archiveBaseName.set("${archiveBaseName.get()}-${name}")
                }
                artifact(dokkaJar)
            }
        }
    }

    val isRelease = project.hasProperty("release")
    signing {
        isRequired = isRelease
        if (isRelease) {
            useGpgCmd()
            sign(publishing.publications)
        }
    }

    // Resolves issues with .asc task output of the sign task of native targets.
    // See: https://github.com/gradle/gradle/issues/26132
    // And: https://youtrack.jetbrains.com/issue/KT-46466
    tasks.withType<Sign>().configureEach {
        val pubName = name.removePrefix("sign").removeSuffix("Publication")

        // These tasks only exist for native targets, hence findByName() to avoid trying to find them for other targets

        // Task ':linkDebugTest<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
        tasks.findByName("linkDebugTest$pubName")?.let {
            mustRunAfter(it)
        }
        // Task ':compileTestKotlin<platform>' uses this output of task ':sign<platform>Publication' without declaring an explicit or implicit dependency
        tasks.findByName("compileTestKotlin$pubName")?.let {
            mustRunAfter(it)
        }
    }
}

apply(plugin = "io.github.gradle-nexus.publish-plugin")

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            useStaging.set(true)
        }
    }
}
