plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
}

// The demo consumes the published JitPack artifact, exactly as any outside project would. When
// working on the library itself, run with -Pstealthkit.local to use a build published to
// mavenLocal instead (./gradlew publishJvmPublicationToMavenLocal from the repo root first).
val stealthkitVersion = "1.2.0"
val stealthkit = if (providers.gradleProperty("stealthkit.local").isPresent) {
    "io.github.malithabandara:kmp-stealth-game-toolkit-jvm:$stealthkitVersion"
} else {
    "com.github.MalithaBandara.kmp-stealth-game-toolkit:kmp-stealth-game-toolkit-jvm:v$stealthkitVersion"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(stealthkit)

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
