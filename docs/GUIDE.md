# Usage guide

This guide walks through building a small stealth level with the toolkit: how the pieces fit a game
loop, then each package in turn with working code. Everything shown here is used for real in
[`demo/src/main/kotlin/Game.kt`](../demo/src/main/kotlin/Game.kt). When a snippet leaves something
out, that file has the full version.

- [Core ideas](#core-ideas)
- [The game loop](#the-game-loop)
- [geometry](#geometry)
- [vision](#vision): cones and detection
- [actors](#actors): guards and footstep noise
- [sentry](#sentry): cameras
- [hazards](#hazards): lasers
- [mechanisms](#mechanisms): levers
- [platforms](#platforms): lifts, conveyors, hook crates
- [physics](#physics): tumbling boxes
- [progress](#progress): checkpoints
- [powerups](#powerups): gadgets
- [follow](#follow): smooth camera
- [layout](#layout): screen sizing
- [terrain](#terrain): rough edges
- [Recipes](#recipes)

## Core ideas

- **Plain data and math.** No type touches a renderer, input system, clock, or platform API. You
  call `update(...)` and read positions, angles and flags back.
- **World coordinates are y-down.** The origin is top-left and `y` grows toward the floor, matching
  most 2D renderers (Compose, KorGE, HTML canvas, SVG). Angles are radians, measured so that `0` points
  right and `PI / 2` points **down**.
- **Axis-aligned rectangles are the collision and occlusion primitive.** A `Rect` is
  `(x, y, width, height)` with `x, y` at the top-left.
- **You own the rules.** The library tells you a guard *can see* the player. Whether that fails the
  level, fills a meter, or plays a sound is up to your game. The same goes for checkpoints (the
  library tracks where to respawn, you decide what a respawn resets) and gadgets (the library
  tracks which gadgets are running, you apply the effect and keep the player's stock).
- **Two kinds of clock.** Most types step with a frame delta: `update(dt)`. Cycle-based things take
  the total level time instead, such as `Laser.update(totalElapsedSeconds)` and the `totalElapsedSeconds`
  argument of `MovingPlatform.update`. With absolute time, a pattern's phase never drifts, even if frames stutter.

## The game loop

A typical frame. The order matters: move the world first so the player rides this frame's platform
motion, then move the player, then check what can see them.

```kotlin
fun step(dt: Double) {
    time += dt

    // 1. World
    lasers.forEach { it.update(time) }
    val liftMove = lift.update(dt, time)          // returns this frame's displacement
    if (!activePowerups.isSmokeScreenActive) cameras.forEach { it.update(dt) }
    guards.forEach { it.update(dt, obstacles = walls) }
    activePowerups.update(dt)

    // 2. Player (your code): input, gravity, collision against solids,
    //    plus liftMove if they're standing on the lift.
    movePlayer(dt, liftMove)

    // 3. Hazards, footsteps, checkpoints
    for (laser in lasers) {
        if (!activePowerups.isInvisibilityActive && laser.intersectsPlayer(player.bounds)) {
            if (!activePowerups.consumeLaserShield()) caught()
        }
    }
    val noiseRadius = if (activePowerups.isNoiseSuppressed) 0.0 else player.noiseLevel.radius
    Noise.alertGuards(player.center, player.x, noiseRadius, guards, occluders)
    checkpoints.update(player.bounds, isSafe = player.isGrounded && !inVision && alert == 0.0)

    // 4. Detection - nobody sees an invisible player; jammed cameras see nothing
    if (!activePowerups.isInvisibilityActive) {
        val points = playerVisibilityPoints()
        for (guard in guards) {
            if (VisionSystem.isPlayerSpotted(guard, points, occluders)) { /* react */ }
        }
    }

    // 5. View
    cameraX = follow.update(target = player.x - viewWidth * 0.4, dt = dt)
}
```

## geometry

`Vec2d`, `Segment2d`, `Rect` and `GeometryUtils`. You'll mostly build `Rect`s for your level and pass
lists of them around as obstacles and occluders.

```kotlin
val wall = Rect(x = 400.0, y = 300.0, width = 30.0, height = 114.0)
wall.intersects(player.bounds)       // overlap test (touching edges don't count)
wall.contains(Vec2d(410.0, 350.0))   // point test (edges count)

GeometryUtils.hasLineOfSight(from = eye, to = target, occluders = walls)
GeometryUtils.castRay(origin = eye, angle = 0.0, range = 300.0, occluders = walls) // hit point
GeometryUtils.normalizeAngle(angle)   // into -PI..PI
```

## vision

`VisionSystem` answers two questions: *what does the cone look like* (for drawing) and *can it see
the player* (for gameplay). Both work for anything with an eye position, a facing angle, a range and
a field of view. `Guard` and `Camera` have convenience overloads.

```kotlin
// Drawing: a polygon (first point is the eye), cut off by occluders with crisp shadow edges.
val cone: List<Vec2d> = VisionSystem.computeVisionPolygon(
    origin = guard.eyePosition,
    facingAngle = guard.facingAngle,
    range = guard.visionRange,
    fov = guard.visionFov,
    occluders = occluders
)

// Gameplay: check several points on the player, not just one, so a body half behind cover is
// still spotted once enough of it shows.
val points = listOf(head, leftShoulder, rightShoulder, feet)
val distance: Double? = VisionSystem.getPlayerSpottedDistance(guard, points, occluders)
val spotted: Boolean = VisionSystem.isPlayerSpotted(camera, points, occluders)
```

`distance` is useful for scaling how fast suspicion builds (closer means faster).

**Occluders** are just `Rect`s. Include moving things such as platforms, crates and the hook crate by
rebuilding the list each frame. It's cheap.

## actors

### Guard

Patrols between `patrolMinX` and `patrolMaxX`, turning around at the ends or at any obstacle you
pass to `update`. When pulled into investigating, he stops and looks at the point of interest for
`investigateDuration` seconds, then resumes his route. He doesn't leave his patrol segment.

```kotlin
val guard = Guard(
    x = 600.0, y = floorY - 48.0,
    patrolMinX = 490.0, patrolMaxX = 730.0,
    facing = -1.0,
    visionRange = 220.0,
    patrolPauseDuration = 0.8,     // stands at each end before turning
    investigateDuration = 3.0,
)

guard.update(dt, obstacles = listOf(crateA, crateB))
```

Reacting to detection is your call. A common pattern:

```kotlin
if (spottedDistance != null) {
    if (guard.state == GuardState.PATROL) guard.startInvestigating(playerX)
    else guard.onPlayerSpottedWhileInvestigating(playerX)
} else if (guardWasSeeingPlayerLastFrame) {
    guard.onVisualLost(lastSeenX)
}
```

Other useful members: `eyePosition` (where the cone starts, at the torch), `facingAngle`,
`isWalking` (drives a walk animation), `visionTilt` (aims the cone downward, for a guard on a
ledge), `returnToPatrol()`. `holdUntilPlayerCrouches` is a flag for your loop to enforce, as the
game does: skip `update` on that guard until your release condition happens.

### Noise

Footstep noise, as in the game. The player's `NoiseLevel` is a hearing radius: `SILENT` (0,
standing still or crouch-walking), `LOW` (100), `NORMAL` (180, walking) or `HIGH` (280). Every
guard inside the radius **with a clear line to the player** turns to investigate. Walls block
sound the same way they block sight.

```kotlin
val noise = if (walking && !crouching) NoiseLevel.NORMAL else NoiseLevel.SILENT
val radius = if (activePowerups.isNoiseSuppressed) 0.0 else noise.radius   // stealth boots
val heard: List<Guard> = Noise.alertGuards(player.center, player.x, radius, guards, occluders)
```

## sentry

`Camera` sweeps between `minAngle` and `maxAngle`. It can pause at each end, it freezes while it's
watching the player, and it holds for `detectionPauseDuration` after losing them.

```kotlin
val cam = Camera.createSweeping(
    x = 1290.0, y = 180.0,              // mount position (top-left of a 20x20 bracket)
    centerAngle = PI / 2,               // looking straight down
    sweepAngleDelta = 50.0 * PI / 180,  // +/- 50 degrees
    sweepSpeed = 0.6,                   // radians per second
    visionRange = 280.0,
    visionFov = 40.0 * PI / 180,
)

cam.update(dt)
if (VisionSystem.isPlayerSpotted(cam, points, occluders)) cam.onPlayerSpotted() else cam.onVisualLost()
```

For drawing, `pivotPosition` is the ball joint and `eyePosition` is the lens tip, which rotates with
`currentAngle`.

## hazards

`Laser` is a beam between a top and bottom point, tilted at most 45 degrees from vertical. It cycles on
and off, or stays on permanently.

```kotlin
val beam = Laser(
    id = "sweep",
    topX = 1640.0, topY = 250.0,
    bottomX = 1690.0, bottomY = 414.0,
    activeDuration = 1.8, inactiveDuration = 1.4,
)
val gate = Laser(id = "gate", topX = 1900.0, topY = 250.0, bottomY = 414.0,
                 isAlwaysActive = true, mechanismId = "gate-switch")

beam.update(totalElapsedSeconds = time)
if (beam.intersectsPlayer(player.bounds)) respawn()

// A switch the player throws: kill every laser wired to it, until reset().
lasers.filter { it.mechanismId == "gate-switch" }.forEach { it.disable() }
```

Timing helpers for UI or AI: `timeUntilInactive(time)` and `remainingInactiveTime(time)`.

`LaserDef` is the same data as a declarative value for level files.

## mechanisms

`Lever` is the game's lever: a switch the player throws with interact. `targetMechanismId` names
what it drives, and you do the wiring. This is the game's `triggerLever`:

```kotlin
fun triggerLever(lever: Lever) {
    lever.isActivated = true
    val target = lever.targetMechanismId ?: return
    hookCrates.filter { it.id == target }.forEach { it.detach() }
    platforms.filter { it.id == target }.forEach { it.activate() }
    lasers.filter { it.mechanismId == target }.forEach { it.disable() }
}

val reachable = levers.firstOrNull { !it.isActivated && it.isPlayerInRange(player.bounds) }
if (interactPressed && reachable != null) triggerLever(reachable)
```

## platforms

### MovingPlatform

Eases back and forth on a cosine, horizontally, vertically, or both. `update` returns how far it
moved this frame. Add that to anything standing on it.

```kotlin
val lift = MovingPlatform(id = "lift", y = 384.0, width = 80.0, height = 14.0,
                          minX = 905.0, maxX = 1020.0, periodSeconds = 4.5)

val move = lift.update(dt, time)
if (player.standingOn == lift) { player.x += move.dx; player.y += move.dy }
```

You can gate a platform with `startsInactive = true` so it waits for `activate()`, add a wind-up with
`activationDelaySeconds`, or make it play one excursion per activation with `oneShot = true`.
`crushesOnContact`, `crushesOnlyFromBelow`, `squeezes` and `noGroundBoarding` are flags for your
collision code to enforce. The platform itself only moves.

### Conveyor and ConveyorCrate

A `ConveyorDef` is just a rect and a surface speed. Crates don't know about belts. You feed each one
the distance its surface moved:

```kotlin
val belt = ConveyorDef(Rect(2000.0, 400.0, 300.0, 14.0), speed = 60.0)
val crate = ConveyorCrate(initialX = 2010.0, initialY = 370.0, width = 30.0, height = 30.0,
                          loopMinX = 2000.0, loopMaxX = 2300.0, shouldLoop = true)

crate.update(dx = belt.speed * crate.speedMultiplier * dt, totalElapsedSeconds = time)
```

Crates can loop, patrol between two bounds (`isPatrol`), or bob vertically (`minY`/`maxY` over
`verticalPeriodSeconds`).

### HookCrate

A crate on a rope. With `physical = true` it swings as a damped pendulum when its rig travels
(`sweepX`). When you `detach()` it, it becomes a real tumbling `RigidBox`:

```kotlin
val load = HookCrate(
    id = "load",
    hook = Rect(2440.0, 150.0, 20.0, 10.0),
    bounds = Rect(2425.0, 270.0, 50.0, 50.0),
    ropeLength = 110.0,
    sweepX = 90.0, sweepPeriodSeconds = 6.0,
    physical = true,
)

load.advanceSweep(dt)
load.detach()                                  // e.g. when the player cuts the rope

load.body?.takeIf { !it.isAsleep }?.let { body ->
    val supports = HashSet<BoxObstacle>()
    BoxPhysics.step(body, dt, obstacles, supports)
    BoxPhysics.settleIfResting(body, dt, supports.isNotEmpty(), supports.minOfOrNull { it.rect.top })
    load.syncBodyBounds()                      // keeps load.bounds on the body
}
```

Draw it at `drawCenterX`, `drawCenterY` rotated by `drawAngle`. Once `body.isAsleep`, treat
`load.bounds` as a solid the player can stand on or push.

## physics

`BoxPhysics` simulates **one** `RigidBox` against axis-aligned `BoxObstacle`s using impulse contacts,
restitution, friction and sleeping. It isn't a general engine: there's no box-vs-box contact and no
polygons. It exists so a dropped crate can tumble and settle believably. `HookCrate` above is the
usual way in. You can also build a `RigidBox` yourself and step it the same way.

## progress

The game's checkpoints. A `Checkpoint` is a respawn point (the player's top-left) with an optional
`triggerZone`. `CheckpointProgress` is the bookkeeping from the game's `GameWorld`:

- **With checkpoints:** each one is secured, in order and never backwards, when the player enters
  its trigger zone. The default zone is a small box around the spawn point.
- **With none:** the level checkpoints itself every 250 units of progress.

```kotlin
val checkpoints = CheckpointProgress(
    manualCheckpoints = listOf(
        Checkpoint(x = 1109.0, y = 370.0, triggerZone = Rect(1080.0, 214.0, 80.0, 200.0), id = "lift"),
        Checkpoint(x = 2629.0, y = 246.0, id = "ledge"),
    ),
    startX = 29.0, startY = 370.0,
)

// Every frame. The game only secures one while the player is safe: grounded, unseen, no alert.
if (checkpoints.update(player.bounds, isSafe = grounded && !inVision && alert == 0.0)) showToast("Checkpoint!")

// Caught, with the Checkpoints gadget active:
player.x = checkpoints.lastCheckpointX; player.y = checkpoints.lastCheckpointY
```

Make a trigger zone tall and wide enough that a jump can't clear it (the demo uses 80 x 200).
`reset()` goes back to the start.

## powerups

The game's gadgets, as a direct port of its `Powerup.kt`: `PowerupType` (id, store name, short name,
duration, price) and `ActivePowerups` (what's running). Each effect is a one-line check in your loop:

| `PowerupType` | Store name | Lasts | You apply it by... |
| --- | --- | --- | --- |
| `SMOKE_SCREEN` | Camera Jammer | 10 s | skipping camera updates and camera detection while `isSmokeScreenActive` |
| `LASER_SHIELD` | Guard Shield | one hit | calling `consumeLaserShield()` when the player touches a laser |
| `INVISIBILITY` | Invisibility Cloak | 10 s | skipping detection and laser hits while `isInvisibilityActive` |
| `NOISE_SUPPRESSION` | Stealth Boots | the level | using a noise radius of 0 while `isNoiseSuppressed` |
| `CHECKPOINTS` | Checkpoints | the level | respawning at the checkpoint instead of restarting while `isCheckpointsActive` |
| `REMOTE_TRIGGER` | Remote Trigger | instant | throwing the nearest unthrown lever (see below) |

How the game fires one. This is its `activatePowerup` and its quick-slot's `tryActivatePowerup`:

```kotlin
fun activatePowerup(type: PowerupType): Boolean {
    if (activePowerups.isActive(type)) return false           // never reset a running gadget
    if (type == PowerupType.REMOTE_TRIGGER) {
        val target = levers.filter { !it.isActivated }
            .minByOrNull { player.center.distanceTo(Vec2d(it.centerX, it.centerY)) } ?: return false
        triggerLever(target)
        return true
    }
    activePowerups.activate(type)
    if (type == PowerupType.SMOKE_SCREEN) cameras.forEach { it.resetDetectionPause() }
    return true
}

fun tryActivatePowerup(type: PowerupType) {
    // Refuse BEFORE spending, so a press that would do nothing never burns one from stock.
    if (activePowerups.isActive(type)) return
    if (type == PowerupType.REMOTE_TRIGGER && levers.none { !it.isActivated }) return
    if (stock.consume(type)) activatePowerup(type)       // stock: your own inventory
}
```

After a checkpoint respawn the game sets `activePowerups.invisibilityTimer = 3.0` and gives lasers
the same 3 s of grace, so the player isn't caught again before they can move.
`PowerupType.fromId("camera_jammer")` resolves the store's ids and aliases.

## follow

`SmoothFollow` is a critically damped spring for one value, usually the camera's x. It chases a
target smoothly without the jolt a plain lerp gives when the target stops or turns.

```kotlin
val follow = SmoothFollow(smoothTime = 0.25).apply { snapTo(startX) }
cameraX = follow.update(target = desiredX, dt = dt, snapIfFartherThan = 600.0)  // snaps on respawn
```

## layout

`ScreenLayout` sizes a game authored against a fixed design canvas (`DESIGN_WIDTH` x
`DESIGN_HEIGHT`, 1040x480) to any screen without letterboxing. Wider screens show more to the sides,
taller ones get more space above the action. Very square tablets get a capped zoom.

```kotlin
DeviceScreen.publish(widthDp = windowWidthDp, heightDp = windowHeightDp)   // on resize
val vp = DeviceScreen.viewport                                             // virtual canvas size

val scale = screenWidthPx / vp.width
val offsetY = vp.height - ScreenLayout.DESIGN_HEIGHT   // pin the floor to the bottom
```

For safe areas, use `ScreenLayout.safeInsetsInVirtualUnits(metrics)` or `DeviceScreen.safeInsets`.

## terrain

`RoughBlock.outline` gives a platform a jittered top edge, so it reads as rough concrete rather than
a ruler-straight box. It's for drawing only. Keep colliding against the plain `Rect`.

```kotlin
val outline = RoughBlock.outline(width = block.width, height = block.height, seed = block.x.toLong())
// Points are relative to the block's top-left: add block.x / block.y when drawing.
```

## Recipes

**Suspicion meter instead of instant fail.** Fill it while any watcher sees the player, faster when
they're close. Drain it otherwise:

```kotlin
val fill = sightings.maxOfOrNull { (distance, range) -> 0.5 + 1.5 * (1 - distance / range) } ?: 0.0
suspicion = if (fill > 0) suspicion + fill * dt else maxOf(0.0, suspicion - 0.5 * dt)
if (suspicion >= 1.0) respawn()
```

**Respawning like the game** (`GameWorld.respawnAtCheckpoint`). On capture, with
`isCheckpointsActive`: move the player to `lastCheckpointX` and `lastCheckpointY`, `reset()` the
mechanisms (cameras, lasers, levers, platforms, hook crates), `returnToPatrol()` the guards, zero the
meter, then set `invisibilityTimer = 3.0` and give lasers 3 s of grace. Without it,
`checkpoints.reset()` and start the level over.

**Proving a level is beatable.** Keep your level logic free of rendering, so a test can drive it
with scripted input. [`demo/src/test/kotlin/PlaythroughTest.kt`](../demo/src/test/kotlin/PlaythroughTest.kt)
plays the demo level start to finish, reacting to lifts, laser windows and patrols. Level changes
that break it fail the build.
