// The Android SDK is only present in CI (and on a developer machine). Without it we build
// `:core` alone and never consult Google Maven, which is unreachable in the local container.
// pluginManagement must be the first block, so the detection is repeated below for the rest of the script.
pluginManagement {
    val sdkPresent = run {
        val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!env.isNullOrBlank() && File(env).isDirectory) return@run true
        val localProps = File(settings.rootDir, "local.properties")
        if (!localProps.isFile) return@run false
        val props = java.util.Properties().apply { localProps.inputStream().use { load(it) } }
        val sdkDir = props.getProperty("sdk.dir")
        !sdkDir.isNullOrBlank() && File(sdkDir).isDirectory
    }
    repositories {
        if (sdkPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val androidSdkPresent: Boolean = run {
    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!env.isNullOrBlank() && File(env).isDirectory) return@run true
    val localProps = File(rootDir, "local.properties")
    if (!localProps.isFile) return@run false
    val props = java.util.Properties().apply { localProps.inputStream().use { load(it) } }
    val sdkDir = props.getProperty("sdk.dir")
    !sdkDir.isNullOrBlank() && File(sdkDir).isDirectory
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (androidSdkPresent) google()
        mavenCentral()
    }
}

rootProject.name = "bidu"

include(":core")
if (androidSdkPresent) {
    include(":app")
} else {
    logger.lifecycle("Bidu: no Android SDK found (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties sdk.dir); building :core only.")
}
