dependencyResolutionManagement {
    repositories {
      gradlePluginPortal()
      mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "skorm-gradle-plugin"

include(":skorm-common")
include(":skorm-core")

project(":skorm-common").projectDir = file("../skorm-common")
project(":skorm-core").projectDir = file("../skorm-core")
