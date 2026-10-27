// All Gradle plugins must live in ONE classloader: the Kotlin Android plugin and KSP reference AGP
// classes (e.g. com.android.build.gradle.api.BaseVariant), so declaring Kotlin here while AGP sits in
// app/build.gradle.kts splits them across parent/child classloaders and :app fails to configure.
// AGP comes from Google Maven, which is only consulted when an Android SDK is present (settings
// then includes :app). It is therefore added to this script's classpath conditionally, and
// app/build.gradle.kts applies it by id without a version.
buildscript {
    if (findProject(":app") != null) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradle.plugin)
        }
    }
}

// Plugins that come from Maven Central / the Gradle Plugin Portal are declared here (apply false)
// so every module shares the root classloader.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
