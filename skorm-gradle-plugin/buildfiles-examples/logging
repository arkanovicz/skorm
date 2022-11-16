import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.gradle.build-scan") version "3.0"
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.10.1"
    id("org.jlleitschuh.gradle.ktlint") version "9.1.0"
}

group = "net.ltgt.gradle"

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

tasks.withType<KotlinCompile>().configureEach {
    // This is the version used in Gradle 5.6, for backwards compatibility when we'll upgrade
    kotlinOptions.apiVersion = "1.3"

    kotlinOptions.allWarningsAsErrors = true
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.5.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    testImplementation("com.google.guava:guava:28.1-jre")

    testApi("com.google.truth:truth:1.0")
    components {
        // See https://github.com/google/truth/issues/333
        withModule("com.google.truth:truth") {
            withVariant("compile") {
                withDependencies {
                    removeIf { it.group == "junit" && it.name == "junit" }
                }
            }
        }
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

gradlePlugin {
    plugins {
        register("conflicts") {
            id = "net.ltgt.logging-framework.conflicts"
            displayName = "Declare conflicts between logging frameworks"
            implementationClass = "net.ltgt.gradle.logging.LoggingFrameworkPlugin"
        }
        register("singleFloggerBackend") {
            id = "net.ltgt.logging-framework.single-flogger-backend"
            displayName = "Enforce a single Flogger backend dependency"
            implementationClass = "net.ltgt.gradle.logging.SingleFloggerBackendPlugin"
        }
        register("singleJCLProvider") {
            id = "net.ltgt.logging-framework.single-jcl-provider"
            displayName = "Enforce a single Commons Logging provider dependency"
            implementationClass = "net.ltgt.gradle.logging.SingleJCLProviderPlugin"
        }
        register("singleJULBridge") {
            id = "net.ltgt.logging-framework.single-jul-bridge"
            displayName = "Enforce a single java.util.logging bridge dependency"
            implementationClass = "net.ltgt.gradle.logging.SingleJULBridgePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-logging-framework-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-logging-framework-plugin"
    tags = listOf("logging", "dependency-management", "dependencies", "log4j", "slf4j", "commons-logging", "flogger")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
    }
}

ktlint {
    version.set("0.35.0")
    enableExperimentalRules.set(true)
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
    compileClasspath = files(sourceSets.test.map { it.output }, sourceSets.main.map { it.output }, configurations.named(compileClasspathConfigurationName))
    runtimeClasspath = files(output, sourceSets.test.map { it.output }, sourceSets.main.map { it.output }, configurations.named(runtimeClasspathConfigurationName))
}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.named(functionalTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(functionalTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks {
    val functionalTest by registering(Test::class) {
        testClassesDirs = functionalTestSourceSet.output.classesDirs
        classpath = functionalTestSourceSet.runtimeClasspath

        project.findProperty("test.gradle-version")?.also {
            systemProperty("test.gradle-version", it)
        }

        mustRunAfter(test)
    }

    check {
        dependsOn(functionalTest)
    }
}
