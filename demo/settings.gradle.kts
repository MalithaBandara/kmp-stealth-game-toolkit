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
        maven("https://jitpack.io")
        mavenLocal {
            content { includeGroup("io.github.malithabandara") }
        }
    }
}

// A standalone build on purpose: the demo consumes the library exactly the way any outside
// project would - from JitPack - rather than as a sibling Gradle module.
rootProject.name = "stealthkit-demo"
