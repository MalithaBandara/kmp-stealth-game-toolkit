plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

// JitPack resolves this as com.github.MalithaBandara:kmp-stealth-game-toolkit:<tag> regardless of
// the group/version below (that's JitPack's own coordinate scheme) - group/version here only
// matter for mavenLocal() during local development (see demo/) and for a future Maven Central
// publish. See README for why the internal group and the JitPack coordinate differ.
group = "io.github.malithabandara"
version = "1.1.0"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm()

    androidTarget {
        publishLibraryVariants("release")
    }

    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.malithabandara.stealthkit"
    compileSdk = 34
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("KMP Stealth Game Toolkit")
            description.set(
                "Pure Kotlin Multiplatform stealth-game toolkit: vision cones, patrolling " +
                    "guards, sweeping cameras, lasers, moving platforms, a camera-follow spring, " +
                    "a responsive-canvas layout adapter, and a small box-physics solver."
            )
            url.set("https://github.com/MalithaBandara/kmp-stealth-game-toolkit")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://github.com/MalithaBandara/kmp-stealth-game-toolkit/blob/main/LICENSE")
                }
            }
        }
    }
}
