plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.kotlin.plugin)
}

gradlePlugin {
  plugins {
    register("skormGradlePlugin") {
      id = "skorm.gradle.plugin"
      implementationClass = "com.republicate.skorm.SkormGradlePlugin"
    }
  }
}
