plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.antlr)
    `java-gradle-plugin`
    `maven-publish`
    signing
    alias(libs.plugins.nexusPublish)
}

gradlePlugin {
  plugins {
    register("skormGradlePlugin") {
      id = "skorm.gradle.plugin"
      implementationClass = "com.republicate.skorm.SkormGradlePlugin"
    }
  }
}


repositories {
     mavenCentral()
 }

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.antlr.kotlin)
    }
}

kotlin.sourceSets.main {
    kotlin.srcDirs(
        file("$buildDir/generated-src/main/kotlin"),
    )
}

dependencies {
    implementation(project(":skorm-common"))
    implementation(project(":skorm-core"))
    implementation(libs.velocity.engine.core)
    implementation(libs.velocity.tools.generic)
    implementation(libs.evo.inflector)
    api(libs.kddl)
    testImplementation(gradleTestKit())
    /*
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    */
    // as api to expose CharStream
    api(libs.antlr.kotlin)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
        kotlinOptions {
            languageVersion = "1.7"
            apiVersion = "1.7"
        }
    }
    withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}

tasks.register<com.strumenta.antlrkotlin.gradle.AntlrKotlinTask>("generateKotlinGrammarSource") {
    /*
    antlrClasspath = configurations.detachedConfiguration(
        // project.dependencies.create("com.strumenta.antlr-kotlin:antlr-kotlin-target:6304d5c1c4")
        project.dependencies.create(libs.antlr.kotlin)
    )
    */
    // maxHeapSize = "64m"
    packageName = "com.republicate.skorm.parser"
    arguments = listOf("-no-visitor", "-no-listener")
    source = project.objects
        .sourceDirectorySet("antlr", "antlr")
        .srcDir("src/main/antlr").apply {
            include("*.g4")
        }
    outputDirectory = File("build/generated-src/main/kotlin")
    group = "code generation"
}

tasks.filter { it.name.startsWith("compileKotlin") }.forEach { it.dependsOn("generateKotlinGrammarSource") }


gradlePlugin {
    plugins {
        create("skormPlugin") {
            id = "skorm-gradle-plugin"
            implementationClass = "com.republicate.skorm.SkormGradlePlugin"
            version = "0.4"
        }
    }
 //   isAutomatedPublishing = false
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
        }
    }
}

apply(plugin = "signing")

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
