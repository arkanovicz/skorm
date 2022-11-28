
rootProject.name = "skorm"

include("skorm-common")
include("skorm-core")
include("skorm-jdbc")
include("examples:bookshelf")
include("skorm-api-client")
include("skorm-api-server")
include("skorm-gradle-plugin")

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "skorm-gradle-plugin") {
                useModule("com.republicate.skorm:skorm-gradle-plugin:0.3")
            } /* else if (requested.id.id == "atomicfu-gradle-plugin") {
                useModule("com.republicate.skorm:skorm-gradle-plugin:0.1")
            } */
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal() // to be able to access skorm-gradle-plugin and essential-kson during development
    }
}
