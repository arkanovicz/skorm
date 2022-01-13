plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

 repositories {
     mavenCentral()
     mavenLocal() // for now, to get kddl 0.4
     maven("https://jitpack.io") // for antlr-kotlin
 }

dependencies {
    implementation(gradleApi())
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    implementation("org.apache.velocity.tools:velocity-tools-generic:3.1")
    api("com.republicate.kddl:kddl:0.4")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks {
    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
        kotlinOptions {
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }
    withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}

gradlePlugin {
    plugins {
        create("skormPlugin") {
            id = "skorm-gradle-plugin"
            implementationClass = "com.republicate.skorm.SkormGradlePlugin"
            version = "0.1"
        }
    }
    isAutomatedPublishing = false
}

publishing {
    publications {
        create<MavenPublication>("skorm-gradle-plugin") {
            from(components["java"])
            pom {
                name.set("skorm-gradle-plugin")
                description.set("skorm-gradle-plugin $version - SKORM code generation gradle plugin")
                url.set("http://gitlab.republicate.com/claude/skorm")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Claude Brisson")
                        email.set("claude.brisson@gmail.com")
                        organization.set("republicate.com")
                        organizationUrl.set("https://republicate.com")
                    }
                }
                scm {
                    connection.set("scm:git@gitlab.republicate.com:claude/skorm.git")
                    developerConnection.set("scm:git:ssh://gitlab.republicate.com:claude/skorm.git")
                    url.set("http://gitlab.republicate.com/claude/skorm")
                }
            }
            artifact(tasks["dokkaJar"])
        }
    }
}

