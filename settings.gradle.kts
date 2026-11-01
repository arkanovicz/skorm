
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
        mavenLocal() // last: a Maven-fetched copy has no module metadata (okhttp 5's umbrella jar is empty without it)
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal() // After mavenCentral to ensure external libs use GMM from remote
    }
}
