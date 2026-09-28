pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-stealth-game-toolkit"

// demo/ is a fully separate Gradle build (its own settings.gradle.kts), deliberately NOT included
// here - it pulls in Compose Multiplatform, which this library has no reason to depend on, and
// keeping it out keeps JitPack's build of the library itself fast and unaffected by demo-only
// dependency resolution. See README for how to build/run the demo.
