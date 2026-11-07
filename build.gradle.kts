import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.SigningExtension

description = "Skorm - Simple Kotlin Object-Relational Mapping"

plugins {
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.versions)
}

allprojects {

    group = "com.republicate.skorm"
    version = "0.32"
}

subprojects {

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "signing")
    apply(plugin = "com.vanniktech.maven.publish")

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        // only the Central upload needs signatures; publishToMavenLocal feeds consumers under test
        if (gradle.startParameter.taskNames.any { it.contains("MavenCentral") }) signAllPublications()
        pom {
            name.set(project.name)
            description.set(provider { project.description })
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
                connection.set("scm:git@github.com:arkanovicz/skorm.git")
                url.set("https://github.com/arkanovicz/skorm")
            }
        }
    }

    configure<SigningExtension> {
        useGpgCmd()
    }
}
