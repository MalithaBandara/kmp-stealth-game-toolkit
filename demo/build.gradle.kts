plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
}

repositories {
    maven("https://jitpack.io")
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    // JitPack folds the repo name into the group for a multi-artifact Gradle project - the
    // library's own internal group (io.github.malithabandara) is NOT the JitPack coordinate.
    // See the top-level README's Install section.
    implementation("com.github.MalithaBandara.kmp-stealth-game-toolkit:kmp-stealth-game-toolkit-jvm:v1.1.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
