plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version ("0.18.0")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// tasks.withType(KotlinCompile::class.java).all {
//     kotlinOptions.jvmTarget = "1.8"
// }

gradlePlugin {
    plugins {
        create("com.republicate.skorm.skorm-gradle-plugin") {
            id = "com.republicate.skorm.skorm-gradle-plugin"
            implementationClass = "com.republicate.skorm.SkormGradlePlugin"
            version = "0.1.0"
        }
    }
}

// Configuration Block for the Plugin Marker artifact on Plugin Central
pluginBundle {
    website = "https://republicate.com"
    vcsUrl = "http://gitlab.republicate.com/claude/skorm.git"
    description = "Gradle Plugin for the SKORM Library"
    tags = listOf("gradle",  "plugin",  "skorm",  "kotlin",  "orm")

    plugins {
        getByName("com.republicate.skorm.skorm-gradle-plugin") {
            displayName = "Gradle Plugin for the SKORM Library"
        }
    }

    mavenCoordinates {
        groupId = "com.republicate.skorm"
        artifactId = "skorm-gradle-plugin"
        version = "0.1.0"
    }
}

//tasks.create("setupPluginUploadFromEnvironment") {
//    doLast {
//        val key = System.getenv("GRADLE_PUBLISH_KEY")
//        val secret = System.getenv("GRADLE_PUBLISH_SECRET")
//
//        if (key == null || secret == null) {
//            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
//        }
//
//        System.setProperty("gradle.publish.key", key)
//        System.setProperty("gradle.publish.secret", secret)
//    }
//}
