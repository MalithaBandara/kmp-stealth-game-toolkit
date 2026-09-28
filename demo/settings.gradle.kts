pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Deliberately a separate Gradle build from the library's own settings.gradle.kts (see the
// library's README) so Compose Multiplatform's dependency graph never touches the library's own
// build - JitPack only ever needs to build the library, never this demo.
rootProject.name = "stealth-toolkit-demo"
