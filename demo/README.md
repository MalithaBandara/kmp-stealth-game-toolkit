# Demo

A Compose Multiplatform desktop app that drives every package in the library as one small
scrolling level - written the way a real consumer would use it, depending on the library's actual
published JitPack artifact (see `build.gradle.kts` in this directory), not a local build. No image
assets anywhere in this module: every shape on screen is `drawRect`/`drawCircle`/`drawLine`/`drawPath`.

This is a **separate Gradle build** from the library itself (see the top-level README for why), so
build it from inside this directory.

## Run it

```bash
./gradlew run
```

## What's in the level, left to right

- A patrolling `Guard` with an occluder-aware vision cone (`vision.VisionSystem`), blocked by a
  wall (`geometry.Rect` occluder) - turns amber while investigating a noise, red the instant it has
  direct line of sight.
- A gap crossed by a sliding `MovingPlatform`.
- A sweeping `Camera`, same vision system, same wall.
- Ground made of `terrain.RoughBlock` outlines - a subtle irregular top edge instead of a flat
  rectangle (collision, such as it is in this demo, still uses the plain rect underneath).
- A `Conveyor` belt looping three `ConveyorCrate`s.
- A `HookCrate` hanging above the ground, swinging gently (its rig has a small sweep) - cut it
  loose and it hands off into a real tumbling `physics.RigidBox`, stepped through `physics.BoxPhysics`
  each frame until it settles.
- A `Laser` gate cycling on its own timer.

The gameplay camera itself is `follow.SmoothFollow` tracking the player - watch how it never jolts
even while patrol/investigate state changes are moving the guard around at different speeds. The
whole visible window is sized by `layout.ScreenLayout.viewportFor(...)` from the actual canvas
pixel size every frame - resize the window to see it adapt without ever letterboxing or cropping.

## Controls

- Arrow keys / WASD - move the green player circle
- Space - make a noise at the player's position (the guard breaks patrol and investigates it,
  independent of whether it can see the player)
- C - cut the hanging crate loose

Wiring "spotted" to "do something about it" is left to the caller by design -
`VisionSystem.isPlayerSpotted`/`getPlayerSpottedDistance` just answer the question; this demo calls
`guard.startInvestigating(...)` and `camera.onPlayerSpotted()`/`onVisualLost()` itself, exactly as a
real game would.
