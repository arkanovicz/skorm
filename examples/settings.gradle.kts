rootProject.name = "skorm-examples"
includeBuild("..")
include("bookshelf")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("..")
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.republicate.skorm") {
                useModule("com.republicate.skorm:skorm-gradle-plugin:0.5")
            }
        }
    }
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  /*
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
  */
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
