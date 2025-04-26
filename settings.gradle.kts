
rootProject.name = "skorm"

include("skorm-common")
include("skorm-core")
include("skorm-jdbc")
include("examples:bookshelf")
include("skorm-api-client")
include("skorm-api-server")

pluginManagement {
    includeBuild("skorm-gradle-plugin")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
