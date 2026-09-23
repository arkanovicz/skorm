rootProject.name = "skorm-examples"
includeBuild("..")
include("bookshelf")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal() // the plugin's own dependencies (kddl) resolve here: see below
    }
    includeBuild("..")
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
    gradlePluginPortal()
    mavenLocal() // after mavenCentral like the root build: a locally published kddl before its Central release
  }
}
