# KMP Stealth Game Toolkit

A small, pure Kotlin Multiplatform toolkit for building 2D stealth games: occluder-aware vision
cones, patrolling guards, sweeping security cameras, timed laser hazards, moving platforms and
conveyors, levers, checkpoints and gadgets, plus a few general-purpose utilities that shipped
alongside them.

Zero platform APIs, zero rendering, zero assets - every type here is plain data and math. You
bring the renderer (KorGE, Compose Canvas, LibGDX-on-JVM, raw SVG, whatever); this library just
tells you where things are and what they can see.

Built for and extracted from [**Infiltrate: Shadow Heist**](https://github.com/MalithaBandara/infiltrate-shadow-heist),
a stealth platformer shipping on Android and iOS - every piece here has been driving real,
shipped levels.

## Documentation

**[Usage guide](docs/GUIDE.md)** - start here. It covers:

- [the game loop](docs/GUIDE.md#the-game-loop): how the pieces fit together each frame
- every package with working code: [vision](docs/GUIDE.md#vision), [guards and noise](docs/GUIDE.md#actors), [cameras](docs/GUIDE.md#sentry), [lasers](docs/GUIDE.md#hazards), [levers](docs/GUIDE.md#mechanisms), [platforms](docs/GUIDE.md#platforms), [physics](docs/GUIDE.md#physics), [checkpoints](docs/GUIDE.md#progress), [gadgets](docs/GUIDE.md#powerups), [camera follow](docs/GUIDE.md#follow), [screen sizing](docs/GUIDE.md#layout) and [terrain](docs/GUIDE.md#terrain)
- [recipes](docs/GUIDE.md#recipes): suspicion meters, respawning like the game, and proving a level is beatable

Every public class and function also has KDoc, so your IDE shows the details on hover. For a
complete working example, see the [demo](#demo). For what changed between versions, see the
[changelog](CHANGELOG.md).

## Install

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    // from a plain JVM or Android project:
    implementation("com.github.MalithaBandara.kmp-stealth-game-toolkit:kmp-stealth-game-toolkit-jvm:v1.2.0")
    // (swap the artifact suffix for your platform: -android, -js, -wasm-js, -iosarm64, -iossimulatorarm64)

    // from a Kotlin Multiplatform project's commonMain, this single coordinate resolves the
    // right platform variant automatically via Gradle's variant-aware resolution:
    implementation("com.github.MalithaBandara.kmp-stealth-game-toolkit:kmp-stealth-game-toolkit:v1.2.0")
}
```

**The group is not this project's own Maven `group`** (`io.github.malithabandara`) - JitPack
folds the repository name into the group for a project that publishes more than one Maven artifact
(`com.github.<owner>.<repo>:<artifact>:<tag>`, per JitPack's own multi-module convention), since a
Kotlin Multiplatform build publishes one artifact per target plus a root metadata artifact, all
from a single Gradle project. `demo/build.gradle.kts` depends on the exact coordinate above (the
`-jvm` variant), resolved from JitPack.

## Same code as the game

Every type here is the code that runs in Infiltrate. The only changes are the ones a library needs:
the game's `Player` becomes plain target points or bounds, `CameraFollow` is named `SmoothFollow`,
and comments that referred to specific levels are generalised. The newer pieces (`powerups`,
`mechanisms`, `progress`, `actors.Noise`) are ported from the game the same way.

## What's in it

| Package | What it does |
| --- | --- |
| `geometry` | 2D vector/segment/rect math, segment intersection, raycasting, line-of-sight checks. The foundation everything else sits on. |
| `vision` | `VisionSystem.computeVisionPolygon` - an occluder-aware field-of-view cone as a renderable polygon, with crisp shadow edges at occluder corners. Plus spotted-distance checks against one or several target points, so a body mostly behind cover can still register once enough of it is exposed. |
| `actors` | `Guard` - patrols a route, turns around at obstacles or route ends, investigates a noise or a lost sighting, then returns to patrol. `Noise` - footstep noise that guards hear if nothing solid is in the way. |
| `sentry` | `Camera` - sweeps between two angles, optionally pausing at each end, and pauses its sweep entirely while it has a target in view. |
| `hazards` | `Laser` - a timed on/off beam (tiltable up to 45 degrees from vertical) that can be permanently switched off via a caller-owned trigger id. |
| `progress` | `Checkpoint` + `CheckpointProgress` - the game's checkpoints: trigger zones secured in order, or automatic every 250 units. |
| `mechanisms` | `Lever` - the game's lever: a switch that drives lasers, platforms or hook crates by id. |
| `powerups` | `PowerupType` + `ActivePowerups` - the game's gadgets (Camera Jammer, Guard Shield, Invisibility Cloak, Stealth Boots, Checkpoints, Remote Trigger), ported from its `Powerup.kt`. |
| `platforms` | `MovingPlatform` (smooth sinusoidal motion, gated activation, one-shot re-arming), `ConveyorDef`/`ConveyorCrate` (belt-driven drift, looping, patrolling, vertical bob), and `HookCrate` (a load hanging from a hook - swings as a damped pendulum if the rig travels, hands off into a real tumbling `RigidBox` via `BoxPhysics` the instant it's cut loose). |
| `follow` | `SmoothFollow` - a one-dimensional critically damped spring for a camera (or any value) that should chase a moving target without the jolts a plain lerp produces. |
| `layout` | `ScreenLayout` - sizes a fixed-design-resolution 2D canvas to any device's aspect ratio without ever letterboxing or cropping, plus safe-area-inset conversion into virtual canvas units. |
| `physics` | `BoxPhysics` - a small impulse-based rigid-box solver. See "About the physics" below before assuming it's more than it is. |
| `terrain` | `RoughBlock` - generates a jittered top-edge outline for a platform, so it reads as rough concrete/rooftop instead of a perfect rectangle. Purely a drawing outline; pair it with a plain `Rect` for collision. |

## About the physics

`BoxPhysics` is **not a general physics engine**, and the README would be dishonest if it implied
otherwise. It simulates exactly one box at a time against a list of axis-aligned obstacles (static
rects, or ones riding a movable body like a cart) - no box-vs-box, no arbitrary polygons, no
joints. Within that narrow scope it does real, standard impulse-based physics: accumulated-impulse
contact resolution (the same family of technique used inside Box2D), restitution only on genuine
impacts so a resting box doesn't buzz, Coulomb friction, and a sleep state once a box has settled.
It exists because most 2D game engines - including the one this library was extracted from - don't
ship a physics engine at all, and a general-purpose one is usually overkill for "make a crate that
falls off a hook tumble and settle believably."

## Quick example

```kotlin
import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.vision.VisionSystem

val guard = Guard(x = 0.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 300.0)
val walls: List<Rect> = levelWalls()

fun tick(dt: Double, playerPosition: Rect) {
    guard.update(dt, obstacles = walls)

    val spottedAt = VisionSystem.getPlayerSpottedDistance(
        guard = guard,
        targetPoints = listOf(playerPosition.topLeft, playerPosition.bottomRight),
        occluders = walls
    )
    if (spottedAt != null) {
        // render an alert, fail the level, whatever your game does on detection
    }

    // for rendering the vision cone itself:
    val cone = VisionSystem.computeVisionPolygon(
        origin = guard.eyePosition,
        facingAngle = guard.facingAngle,
        range = guard.visionRange,
        fov = guard.visionFov,
        occluders = walls
    )
}
```

This is deliberately just a taste of one package. The [usage guide](docs/GUIDE.md) covers every
package, and [`demo/src/main/kotlin/Game.kt`](demo/src/main/kotlin/Game.kt) is the complete,
realistic reference: a whole small level built from this library and nothing else.

## Demo

`demo/` is a separate Gradle build (Compose Desktop) containing a small playable stealth level
that uses every package above, pulled from the library's published JitPack artifact and drawn with
nothing but rectangles, circles, lines and paths. No image assets.

```bash
./gradlew -p demo run
```

A scripted playthrough test (`./gradlew -p demo test`) proves the level can be finished without
being caught. See [`demo/README.md`](demo/README.md) for controls and a section-by-section map of
which library types each part of the level uses.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Targets

`jvm`, `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `js`, `wasmJs`. Every type in this library
is pure `kotlin.math`/`kotlin.concurrent` - there are no platform-specific APIs - so all six targets
build clean in CI.

## License

MIT - see [LICENSE](LICENSE).
