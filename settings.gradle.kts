
rootProject.name = "skorm"

include("skorm-common")
include("skorm-core")
include("skorm-jdbc")
include("skorm-api-client")
include("skorm-api-server")
include("skorm-gradle-plugin")
// include("examples:bookshelf")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
