plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

 repositories {
     mavenCentral()
     mavenLocal() // for now, to get latest kddl version
     maven("https://jitpack.io") // for antlr-kotlin
 }

buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.strumenta.antlr-kotlin:antlr-kotlin-gradle-plugin:6304d5c1c4")
    }
}

kotlin.sourceSets.main {
    kotlin.srcDirs(
        file("$buildDir/generated-src/main/kotlin"),
    )
}

dependencies {
    implementation(gradleApi())
    implementation("com.republicate.skorm:skorm-common-jvm:0.3")
    implementation("com.republicate.skorm:skorm-core-jvm:0.3")
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    implementation("org.apache.velocity.tools:velocity-tools-generic:3.1")
    implementation("org.atteo:evo-inflector:1.3")
    api("com.republicate.kddl:kddl:0.7.4")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    // as api to expose CharStream
    api("com.strumenta.antlr-kotlin:antlr-kotlin-runtime:6304d5c1c4")
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

tasks.register<com.strumenta.antlrkotlin.gradleplugin.AntlrKotlinTask>("generateKotlinGrammarSource") {
    antlrClasspath = configurations.detachedConfiguration(
        project.dependencies.create("com.strumenta.antlr-kotlin:antlr-kotlin-target:6304d5c1c4")
    )
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
            version = "0.3"
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
            artifact(tasks["dokkaJar"])
        }
    }
}

