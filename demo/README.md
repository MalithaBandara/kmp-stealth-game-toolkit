# Demo

A Compose Multiplatform desktop app that drives every visible piece of the toolkit - a patrolling
`Guard` with its occluder-aware vision cone, a sweeping `Camera`, a `Laser` cycling on its own
timer, and a `MovingPlatform` - using nothing but `drawRect`/`drawCircle`/`drawLine`/`drawPath`. No
image assets anywhere in this module, on purpose: it's the proof that the library needs none.

This is a **separate Gradle build** from the library itself (see the top-level README for why), so
build it from inside this directory.

## Run it

This module depends on the library's real published JitPack artifact (not a local build), so it
works straight away:

```bash
./gradlew run
```

## Controls

- Arrow keys / WASD - move the green player circle
- Space - make a noise at the player's position (the guard breaks patrol and investigates it,
  independent of whether it can see the player)

Guard/camera colors: blue/orange while unaware, amber while a guard is investigating, red the
instant either one has direct line of sight to the player - wiring "spotted" to "do something
about it" is left to the caller (`VisionSystem.isPlayerSpotted`/`getPlayerSpottedDistance` just
answer the question; this demo calls `guard.startInvestigating(...)` and
`camera.onPlayerSpotted()`/`onVisualLost()` itself, exactly as a real game would).

Not visualized here (see their own unit tests for behavior instead): `Conveyor`/`ConveyorCrate`,
`BoxPhysics`, `SmoothFollow`, `ScreenLayout`.
