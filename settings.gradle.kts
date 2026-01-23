
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
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal() // After mavenCentral to ensure external libs use GMM from remote
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
