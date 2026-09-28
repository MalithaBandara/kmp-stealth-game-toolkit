plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
}

repositories {
    mavenLocal() // the library, until it's also resolvable straight from JitPack here
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.github.malithabandara:kmp-stealth-game-toolkit-jvm:1.0.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
