// Plugins that come from Maven Central / the Gradle Plugin Portal are declared here (apply false)
// so every module shares one classloader. The Android Gradle Plugin lives on Google Maven and is
// therefore declared only in app/build.gradle.kts, which is evaluated solely when an SDK exists.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
